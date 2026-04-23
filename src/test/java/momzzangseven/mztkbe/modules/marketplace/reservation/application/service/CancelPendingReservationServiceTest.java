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
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CancelPendingReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CancelPendingReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.EscrowDispatchEvent;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.event.EscrowDispatchEventListener;
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
@DisplayName("CancelPendingReservationService 단위 테스트")
class CancelPendingReservationServiceTest {

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private CancelPendingReservationService sut;

  private static final Long RESERVATION_ID = 1L;
  private static final Long USER_ID = 50L;
  private static final Long OTHER_USER_ID = 999L;

  private Reservation pendingReservation() {
    return Reservation.builder()
        .id(RESERVATION_ID)
        .userId(USER_ID)
        .trainerId(10L)
        .slotId(1L)
        .reservationDate(LocalDate.now().plusDays(2))
        .reservationTime(LocalTime.of(14, 0))
        .durationMinutes(60)
        .status(ReservationStatus.PENDING)
        .orderId("order-cancel")
        .version(0L)
        .build();
  }

  @Nested
  @DisplayName("성공 케이스")
  class 성공 {

    @Test
    @DisplayName("[CP-01] PENDING 예약을 본인이 취소하면 USER_CANCELLED 반환 및 EscrowDispatchEvent 발행")
    void 정상_취소() {
      // given — service saves with PENDING_TX_HASH sentinel; real escrow call happens AFTER_COMMIT
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(pendingReservation()));
      Reservation cancelled =
          pendingReservation().cancelByUser(EscrowDispatchEventListener.PENDING_TX_HASH);
      given(saveReservationPort.save(any())).willReturn(cancelled);

      // when
      CancelPendingReservationResult result =
          sut.execute(new CancelPendingReservationCommand(RESERVATION_ID, USER_ID));

      // then
      assertThat(result.status()).isEqualTo(ReservationStatus.USER_CANCELLED);
      then(saveReservationPort).should().save(any());

      // EscrowDispatchEvent published with CANCEL action
      ArgumentCaptor<EscrowDispatchEvent> eventCaptor =
          ArgumentCaptor.forClass(EscrowDispatchEvent.class);
      then(eventPublisher).should().publishEvent(eventCaptor.capture());
      assertThat(eventCaptor.getValue().action())
          .isEqualTo(EscrowDispatchEvent.EscrowAction.CANCEL);
      assertThat(eventCaptor.getValue().orderId()).isEqualTo("order-cancel");
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class 실패 {

    @Test
    @DisplayName("[CP-02] 존재하지 않는 예약 ID로 요청 시 MARKETPLACE_RESERVATION_NOT_FOUND 예외")
    void 존재하지_않는_예약() {
      // given
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(
              () -> sut.execute(new CancelPendingReservationCommand(RESERVATION_ID, USER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("[CP-03] 타인 예약 취소 시도 시 MarketplaceUnauthorizedAccessException 예외")
    void 타인_예약_취소_시도() {
      // given
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
          .willReturn(Optional.of(pendingReservation()));

      // when & then
      assertThatThrownBy(
              () -> sut.execute(new CancelPendingReservationCommand(RESERVATION_ID, OTHER_USER_ID)))
          .isInstanceOf(MarketplaceUnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("[CP-04] APPROVED 상태 예약 취소 시도 시 MARKETPLACE_RESERVATION_INVALID_STATUS 예외")
    void 승인된_예약_취소_시도() {
      // given: PENDING → APPROVED 전환된 예약
      Reservation approved = pendingReservation().approve();
      given(loadReservationPort.findByIdWithLock(RESERVATION_ID)).willReturn(Optional.of(approved));

      // when & then
      assertThatThrownBy(
              () -> sut.execute(new CancelPendingReservationCommand(RESERVATION_ID, USER_ID)))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getCode())
                      .isEqualTo(ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS.getCode()));
    }
  }
}
