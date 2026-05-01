package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ApproveReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ApproveReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApproveReservationService 단위 테스트")
class ApproveReservationServiceTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;

  @InjectMocks private ApproveReservationService sut;

  private static final Long RESERVATION_ID = 1L;
  private static final Long TRAINER_ID = 100L;
  private static final Long OTHER_TRAINER_ID = 999L;

  private Reservation pendingReservation() {
    return Reservation.builder()
        .id(RESERVATION_ID)
        .userId(1L)
        .trainerId(TRAINER_ID)
        .slotId(1L)
        .reservationDate(LocalDate.now().plusDays(3))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.PENDING)
        .orderId("order-1")
        .version(0L)
        .build();
  }

  @Nested
  @DisplayName("성공 케이스")
  class 성공 {

    @Test
    @DisplayName("[AP-01] 본인 예약 승인 시 APPROVED 상태 반환")
    void 정상_승인() {
      // given
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(pendingReservation()));
      Reservation approvedRes = pendingReservation().approve();
      given(saveReservationPort.save(any())).willReturn(approvedRes);

      // when
      ApproveReservationResult result =
          sut.execute(new ApproveReservationCommand(RESERVATION_ID, TRAINER_ID));

      // then
      assertThat(result.status()).isEqualTo(ReservationStatus.APPROVED);
      then(saveReservationPort).should().save(any());
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class 실패 {

    @Test
    @DisplayName("[AP-02] 타인 트레이너가 승인 시도 시 MARKETPLACE_UNAUTHORIZED_ACCESS 예외")
    void 타인_트레이너_승인_시도() {
      // given
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(pendingReservation()));

      // when & then
      assertThatThrownBy(
              () -> sut.execute(new ApproveReservationCommand(RESERVATION_ID, OTHER_TRAINER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_UNAUTHORIZED_ACCESS.getCode()));
    }

    @Test
    @DisplayName("[AP-03] APPROVED 상태 예약 재승인 시 MARKETPLACE_RESERVATION_INVALID_STATUS 예외")
    void 이미_승인된_예약_재승인() {
      // given: 이미 APPROVED 상태
      Reservation approved = pendingReservation().approve();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID)).willReturn(Optional.of(approved));

      // when & then
      assertThatThrownBy(
              () -> sut.execute(new ApproveReservationCommand(RESERVATION_ID, TRAINER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS.getCode()));
    }
  }
}
