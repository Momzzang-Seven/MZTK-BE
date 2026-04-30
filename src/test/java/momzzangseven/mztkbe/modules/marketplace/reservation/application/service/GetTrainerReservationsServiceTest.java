package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetTrainerReservationsQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetTrainerReservationsServiceTest {

  @Mock private LoadReservationPort loadReservationPort;

  @InjectMocks private GetTrainerReservationsService sut;

  private Reservation sampleReservation(Long trainerId) {
    return Reservation.builder()
        .id(10L)
        .userId(1L)
        .trainerId(trainerId)
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
  @DisplayName("트레이너 수강 신청 목록 조회 - 상태 필터 없이 전체 목록 반환")
  void execute_NoStatusFilter_ReturnsAll() {
    // given
    List<Reservation> reservations = List.of(sampleReservation(2L), sampleReservation(2L));
    given(loadReservationPort.findByTrainerId(2L, null)).willReturn(reservations);

    // when
    List<ReservationSummaryResult> results = sut.execute(new GetTrainerReservationsQuery(2L, null));

    // then
    assertThat(results).hasSize(2);
    assertThat(results.get(0).trainerId()).isEqualTo(2L);
  }

  @Test
  @DisplayName("트레이너 수강 신청 목록 조회 - APPROVED 상태 필터 적용")
  void execute_ApprovedStatusFilter_ReturnsFilteredList() {
    // given
    Reservation approved =
        sampleReservation(2L).toBuilder().status(ReservationStatus.APPROVED).build();
    given(loadReservationPort.findByTrainerId(2L, ReservationStatus.APPROVED))
        .willReturn(List.of(approved));

    // when
    List<ReservationSummaryResult> results =
        sut.execute(new GetTrainerReservationsQuery(2L, ReservationStatus.APPROVED));

    // then
    assertThat(results).hasSize(1);
    assertThat(results.get(0).status()).isEqualTo(ReservationStatus.APPROVED);
  }

  @Test
  @DisplayName("트레이너 수강 신청 목록 조회 - 수강 신청이 없으면 빈 리스트 반환")
  void execute_NoReservations_ReturnsEmptyList() {
    // given
    given(loadReservationPort.findByTrainerId(2L, null)).willReturn(List.of());

    // when
    List<ReservationSummaryResult> results = sut.execute(new GetTrainerReservationsQuery(2L, null));

    // then
    assertThat(results).isEmpty();
  }

  @Test
  @DisplayName("트레이너 수강 신청 목록 조회 - trainerId가 null이면 IllegalArgumentException")
  void execute_NullTrainerId_ThrowsIllegalArgument() {
    assertThatThrownBy(() -> sut.execute(new GetTrainerReservationsQuery(null, null)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
