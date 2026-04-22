package momzzangseven.mztkbe.modules.marketplace.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.marketplace.application.dto.CompleteReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.CompleteReservationResult;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompleteReservationService 단위 테스트")
class CompleteReservationServiceTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private SubmitEscrowTransactionPort submitEscrowTransactionPort;

  @InjectMocks private CompleteReservationService sut;

  private static final Long RESERVATION_ID = 1L;
  private static final Long USER_ID = 1L;
  private static final Long OTHER_USER_ID = 999L;

  /** APPROVED 예약, 수업 시간은 이미 과거. */
  private Reservation approvedPastReservation() {
    return Reservation.builder()
        .id(RESERVATION_ID)
        .userId(USER_ID)
        .trainerId(100L)
        .slotId(1L)
        .reservationDate(LocalDate.now().minusDays(1)) // 어제 수업
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.APPROVED)
        .orderId("order-1")
        .version(0L)
        .build();
  }

  /** APPROVED 예약, 수업 시간은 미래. */
  private Reservation approvedFutureReservation() {
    return Reservation.builder()
        .id(RESERVATION_ID)
        .userId(USER_ID)
        .trainerId(100L)
        .slotId(1L)
        .reservationDate(LocalDate.now().plusDays(1)) // 내일 수업
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.APPROVED)
        .orderId("order-1")
        .version(0L)
        .build();
  }

  @Nested
  @DisplayName("성공 케이스")
  class 성공 {

    @Test
    @DisplayName("[CM-01] 수업 종료 후 완료 확인 시 SETTLED 상태 반환")
    void 수업_종료_후_완료() {
      // given
      given(loadReservationPort.findById(RESERVATION_ID))
          .willReturn(Optional.of(approvedPastReservation()));
      given(submitEscrowTransactionPort.submitConfirm("order-1")).willReturn("0xCONFIRM");
      Reservation settled = approvedPastReservation().complete("0xCONFIRM");
      given(saveReservationPort.save(any())).willReturn(settled);

      // when
      CompleteReservationResult result =
          sut.execute(new CompleteReservationCommand(RESERVATION_ID, USER_ID));

      // then
      assertThat(result.status()).isEqualTo(ReservationStatus.SETTLED);
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class 실패 {

    @Test
    @DisplayName("[CM-02] 수업 시작 전 완료 시도 시 MARKETPLACE_RESERVATION_EARLY_COMPLETE 예외")
    void 수업_시작_전_완료() {
      // given
      given(loadReservationPort.findById(RESERVATION_ID))
          .willReturn(Optional.of(approvedFutureReservation()));

      // when & then
      assertThatThrownBy(() -> sut.execute(new CompleteReservationCommand(RESERVATION_ID, USER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_EARLY_COMPLETE.getCode()));
    }

    @Test
    @DisplayName("[CM-03] 타인이 완료 시도 시 MARKETPLACE_UNAUTHORIZED_ACCESS 예외")
    void 타인_완료_시도() {
      // given
      given(loadReservationPort.findById(RESERVATION_ID))
          .willReturn(Optional.of(approvedPastReservation()));

      // when & then
      assertThatThrownBy(
              () -> sut.execute(new CompleteReservationCommand(RESERVATION_ID, OTHER_USER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_UNAUTHORIZED_ACCESS.getCode()));
    }

    @Test
    @DisplayName("[CM-04] PENDING 상태 예약 완료 시도 시 MARKETPLACE_RESERVATION_INVALID_STATUS 예외")
    void PENDING_상태_완료_시도() {
      // given: PENDING → SETTLED 전이는 불가
      Reservation pending =
          Reservation.builder()
              .id(RESERVATION_ID)
              .userId(USER_ID)
              .trainerId(100L)
              .slotId(1L)
              .reservationDate(LocalDate.now().minusDays(1))
              .reservationTime(LocalTime.of(10, 0))
              .durationMinutes(60)
              .status(ReservationStatus.PENDING)
              .orderId("order-1")
              .version(0L)
              .build();
      given(loadReservationPort.findById(RESERVATION_ID)).willReturn(Optional.of(pending));

      // when & then
      assertThatThrownBy(() -> sut.execute(new CompleteReservationCommand(RESERVATION_ID, USER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS.getCode()));
    }
  }
}
