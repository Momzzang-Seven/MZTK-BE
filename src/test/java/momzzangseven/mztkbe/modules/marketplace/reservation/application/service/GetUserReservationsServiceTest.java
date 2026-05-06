package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetUserReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetUserReservationsServiceTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private LoadClassSummaryPort loadClassSummaryPort;
  @Mock private LoadUserSummaryPort loadUserSummaryPort;

  @InjectMocks private GetUserReservationsService sut;

  private Reservation sampleReservation(Long userId) {
    return Reservation.builder()
        .id(10L)
        .userId(userId)
        .trainerId(2L)
        .slotId(3L)
        .reservationDate(LocalDate.of(2025, 6, 1))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.PENDING)
        .orderId("order-abc")
        .txHash("tx-abc")
        .build();
  }

  @Test
  @DisplayName("내 예약 목록 조회 - 상태 필터 없이 전체 목록 반환")
  void execute_NoStatusFilter_ReturnsAll() {
    // given
    List<Reservation> reservations = List.of(sampleReservation(1L), sampleReservation(1L));
    given(loadReservationPort.findByUserId(1L, null)).willReturn(reservations);
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    // when
    List<ReservationSummaryResult> results = sut.execute(new GetUserReservationsQuery(1L, null));

    // then
    assertThat(results).hasSize(2);
    assertThat(results.get(0).userId()).isEqualTo(1L);
  }

  @Test
  @DisplayName("내 예약 목록 조회 - PENDING 상태 필터 적용 시 해당 목록만 반환")
  void execute_PendingStatusFilter_ReturnsFilteredList() {
    // given
    given(loadReservationPort.findByUserId(1L, ReservationStatus.PENDING))
        .willReturn(List.of(sampleReservation(1L)));
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    // when
    List<ReservationSummaryResult> results =
        sut.execute(new GetUserReservationsQuery(1L, ReservationStatus.PENDING));

    // then
    assertThat(results).hasSize(1);
    assertThat(results.get(0).status()).isEqualTo(ReservationStatus.PENDING);
  }

  @Test
  @DisplayName("내 예약 목록 조회 - 예약이 없으면 빈 리스트 반환")
  void execute_NoReservations_ReturnsEmptyList() {
    // given
    given(loadReservationPort.findByUserId(1L, null)).willReturn(List.of());
    given(loadClassSummaryPort.findBySlotIds(anyList())).willReturn(Map.of());
    given(loadUserSummaryPort.findByIds(anyList())).willReturn(Map.of());

    // when
    List<ReservationSummaryResult> results = sut.execute(new GetUserReservationsQuery(1L, null));

    // then
    assertThat(results).isEmpty();
  }

  @Test
  @DisplayName("내 예약 목록 조회 - userId가 null이면 IllegalArgumentException")
  void execute_NullUserId_ThrowsIllegalArgument() {
    assertThatThrownBy(() -> sut.execute(new GetUserReservationsQuery(null, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
