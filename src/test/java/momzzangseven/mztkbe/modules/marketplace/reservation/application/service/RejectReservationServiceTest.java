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
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RejectReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RejectReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.sanction.domain.vo.TrainerStrikeEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@DisplayName("RejectReservationService 단위 테스트")
class RejectReservationServiceTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private SubmitEscrowTransactionPort submitEscrowTransactionPort;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private RejectReservationService sut;

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
    @DisplayName("[RJ-01] 반려 성공 시 REJECTED 상태 반환 및 TrainerStrikeEvent 발행")
    void 정상_반려_및_이벤트_발행() {
      // given
      given(loadReservationPort.findById(RESERVATION_ID))
          .willReturn(Optional.of(pendingReservation()));
      given(submitEscrowTransactionPort.submitCancel("order-1")).willReturn("0xCANCEL");
      Reservation rejected = pendingReservation().reject("0xCANCEL");
      given(saveReservationPort.save(any())).willReturn(rejected);

      // when
      RejectReservationResult result =
          sut.execute(new RejectReservationCommand(RESERVATION_ID, TRAINER_ID, "일정 불가"));

      // then
      assertThat(result.status()).isEqualTo(ReservationStatus.REJECTED);

      ArgumentCaptor<TrainerStrikeEvent> eventCaptor =
          ArgumentCaptor.forClass(TrainerStrikeEvent.class);
      then(eventPublisher).should().publishEvent(eventCaptor.capture());
      assertThat(eventCaptor.getValue().trainerId()).isEqualTo(TRAINER_ID);
      assertThat(eventCaptor.getValue().reason()).isEqualTo("REJECT");
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class 실패 {

    @Test
    @DisplayName("[RJ-02] 타인 트레이너가 반려 시도 시 MARKETPLACE_UNAUTHORIZED_ACCESS 예외")
    void 타인_트레이너_반려_시도() {
      // given
      given(loadReservationPort.findById(RESERVATION_ID))
          .willReturn(Optional.of(pendingReservation()));

      // when & then
      assertThatThrownBy(
              () ->
                  sut.execute(new RejectReservationCommand(RESERVATION_ID, OTHER_TRAINER_ID, "이유")))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_UNAUTHORIZED_ACCESS.getCode()));

      then(eventPublisher).shouldHaveNoInteractions();
    }
  }
}
