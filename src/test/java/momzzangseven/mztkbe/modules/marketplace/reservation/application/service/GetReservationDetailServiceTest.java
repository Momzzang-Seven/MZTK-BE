package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.global.error.marketplace.ReservationNotFoundException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.GetReservationResult;
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
class GetReservationDetailServiceTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private LoadClassSummaryPort loadClassSummaryPort;
  @Mock private LoadUserSummaryPort loadUserSummaryPort;

  @InjectMocks private GetReservationDetailService sut;

  private Reservation sampleReservation(Long userId, Long trainerId) {
    return Reservation.builder()
        .id(10L)
        .userId(userId)
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
  @DisplayName("예약 상세 조회 - 예약 소유 유저가 조회하면 성공")
  void execute_OwnerUser_ReturnsDetail() {
    // given
    Reservation reservation = sampleReservation(1L, 2L);
    given(loadReservationPort.findById(10L)).willReturn(Optional.of(reservation));
    given(loadClassSummaryPort.findBySlotId(any())).willReturn(Optional.empty());
    given(loadUserSummaryPort.findById(any())).willReturn(Optional.empty());

    // when
    GetReservationResult result = sut.execute(new GetReservationQuery(10L, 1L));

    // then
    assertThat(result.reservationId()).isEqualTo(10L);
    assertThat(result.status()).isEqualTo(ReservationStatus.PENDING);
  }

  @Test
  @DisplayName("예약 상세 조회 - 담당 트레이너가 조회하면 성공")
  void execute_OwnerTrainer_ReturnsDetail() {
    // given
    Reservation reservation = sampleReservation(1L, 2L);
    given(loadReservationPort.findById(10L)).willReturn(Optional.of(reservation));
    given(loadClassSummaryPort.findBySlotId(any())).willReturn(Optional.empty());
    given(loadUserSummaryPort.findById(any())).willReturn(Optional.empty());

    // when
    GetReservationResult result = sut.execute(new GetReservationQuery(10L, 2L));

    // then
    assertThat(result.trainerId()).isEqualTo(2L);
  }

  @Test
  @DisplayName("예약 상세 조회 - 존재하지 않는 예약이면 ReservationNotFoundException")
  void execute_NotFound_ThrowsReservationNotFoundException() {
    // given
    given(loadReservationPort.findById(999L)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> sut.execute(new GetReservationQuery(999L, 1L)))
        .isInstanceOf(ReservationNotFoundException.class);
  }

  @Test
  @DisplayName("예약 상세 조회 - 소유자도 트레이너도 아닌 요청자면 MarketplaceUnauthorizedAccessException")
  void execute_UnauthorizedRequester_ThrowsUnauthorizedException() {
    // given
    Reservation reservation = sampleReservation(1L, 2L);
    given(loadReservationPort.findById(10L)).willReturn(Optional.of(reservation));

    // when & then
    assertThatThrownBy(() -> sut.execute(new GetReservationQuery(10L, 999L)))
        .isInstanceOf(MarketplaceUnauthorizedAccessException.class);
  }

  @Test
  @DisplayName("예약 상세 조회 - reservationId가 null이면 IllegalArgumentException")
  void execute_NullReservationId_ThrowsIllegalArgument() {
    assertThatThrownBy(() -> sut.execute(new GetReservationQuery(null, 1L)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
