package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionWritePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.ReplayConfirmedReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecoverReservationEscrowServiceTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2024-06-03T01:00:00Z"), ZoneId.of("Asia/Seoul"));
  private static final Long RESERVATION_ID = 10L;
  private static final Long BUYER_ID = 1L;
  private static final Long TRAINER_ID = 2L;
  private static final String ORDER_KEY = "0x" + "0".repeat(63) + "1";

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort;
  @Mock private CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort;
  @Mock private LoadReservationExecutionWritePort loadReservationExecutionWritePort;
  @Mock private ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort;
  @Mock private LoadReservationWalletPort loadReservationWalletPort;
  @Mock private LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort;
  @Mock private LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;

  private RecoverReservationEscrowService sut;

  @BeforeEach
  void setUp() {
    sut =
        new RecoverReservationEscrowService(
            loadReservationPort,
            saveReservationPort,
            prepareReservationEscrowExecutionPort,
            cancelReservationEscrowExecutionPort,
            loadReservationExecutionWritePort,
            replayConfirmedReservationExecutionPort,
            loadReservationWalletPort,
            loadReservationEscrowPaymentConfigPort,
            loadReservationEscrowOrderPort,
            FIXED_CLOCK);
    given(saveReservationPort.save(any()))
        .willAnswer(invocation -> invocation.getArgument(0, Reservation.class));
  }

  @Test
  @DisplayName(
      "purchase recovery 전에 order가 이미 CREATED이면 새 purchase intent를 만들지 않고 local locked로 동기화한다")
  void purchase_recovery_syncs_created_order_without_new_intent() {
    Reservation reservation = reservation(ReservationStatus.PURCHASE_PENDING);
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(order(ReservationEscrowOrderView.STATE_CREATED, 1_900_000_000L));

    RecoverReservationEscrowResult result =
        sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID));

    assertThat(result.status()).isEqualTo(ReservationStatus.PENDING);
    assertThat(result.escrowStatus()).isEqualTo(ReservationEscrowStatus.LOCKED.name());
    assertThat(result.web3()).isNull();
    then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("purchase recovery 전에 order가 terminal이면 local terminal outcome으로 동기화한다")
  void purchase_recovery_syncs_terminal_order_without_new_intent() {
    Reservation reservation = reservation(ReservationStatus.PURCHASE_PENDING);
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(order(ReservationEscrowOrderView.STATE_ADMIN_REFUNDED, 1_900_000_000L));

    RecoverReservationEscrowResult result =
        sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID));

    assertThat(result.status()).isEqualTo(ReservationStatus.TIMEOUT_CANCELLED);
    assertThat(result.escrowStatus()).isEqualTo(ReservationEscrowStatus.REFUNDED.name());
    assertThat(result.web3()).isNull();
    then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("confirm recovery 전에 order가 CONFIRMED이면 새 confirm intent 없이 SETTLED로 동기화한다")
  void confirm_recovery_syncs_confirmed_order_without_new_intent() {
    Reservation reservation = reservation(ReservationStatus.CONFIRM_PENDING);
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(order(ReservationEscrowOrderView.STATE_CONFIRMED, 1_900_000_000L));

    RecoverReservationEscrowResult result =
        sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID));

    assertThat(result.status()).isEqualTo(ReservationStatus.SETTLED);
    assertThat(result.escrowStatus()).isEqualTo(ReservationEscrowStatus.SETTLED.name());
    assertThat(result.web3()).isNull();
    then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("cancel recovery 전에 order가 CANCELLED이면 새 cancel intent 없이 USER_CANCELLED로 동기화한다")
  void cancel_recovery_syncs_cancelled_order_without_new_intent() {
    Reservation reservation = reservation(ReservationStatus.CANCEL_PENDING);
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(order(ReservationEscrowOrderView.STATE_CANCELLED, 1_900_000_000L));

    RecoverReservationEscrowResult result =
        sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID));

    assertThat(result.status()).isEqualTo(ReservationStatus.USER_CANCELLED);
    assertThat(result.escrowStatus()).isEqualTo(ReservationEscrowStatus.REFUNDED.name());
    assertThat(result.web3()).isNull();
    then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName(
      "deadline 이후 cancel pending recovery는 기존 cancel intent 대신 deadline refund intent를 준비한다")
  void expired_cancelPending_recovery_forcesDeadlineRefund() {
    long deadlineEpochSeconds = FIXED_CLOCK.instant().getEpochSecond();
    Reservation reservation =
        reservation(ReservationStatus.CANCEL_PENDING).toBuilder()
            .escrowStatus(ReservationEscrowStatus.CANCEL_PENDING)
            .currentExecutionIntentPublicId("old-cancel-intent")
            .contractDeadlineEpochSeconds(deadlineEpochSeconds)
            .contractDeadlineAt(LocalDateTime.now(FIXED_CLOCK))
            .pendingAction(ReservationEscrowAction.BUYER_CANCEL)
            .pendingAttemptToken("cancel-token")
            .priorStatus(ReservationStatus.PENDING)
            .priorEscrowStatus(ReservationEscrowStatus.LOCKED)
            .build();
    AtomicReference<Reservation> latestSaved = new AtomicReference<>(reservation);
    given(saveReservationPort.save(any()))
        .willAnswer(
            invocation -> {
              Reservation saved = invocation.getArgument(0, Reservation.class);
              latestSaved.set(saved);
              return saved;
            });
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation))
        .willReturn(Optional.of(reservation))
        .willAnswer(invocation -> Optional.of(latestSaved.get()));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(order(ReservationEscrowOrderView.STATE_CREATED, deadlineEpochSeconds));
    given(loadReservationEscrowPaymentConfigPort.load())
        .willReturn(
            new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                "0x3333333333333333333333333333333333333333", 18, 2_592_000L));
    given(prepareReservationEscrowExecutionPort.prepareDeadlineRefund(any()))
        .willReturn(new PrepareReservationEscrowResult(web3()));

    RecoverReservationEscrowResult result =
        sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID));

    assertThat(result.status()).isEqualTo(ReservationStatus.DEADLINE_REFUND_PENDING);
    assertThat(result.web3()).isNotNull();
    then(loadReservationExecutionWritePort).shouldHaveNoInteractions();
    then(prepareReservationEscrowExecutionPort).should().prepareDeadlineRefund(any());
    then(prepareReservationEscrowExecutionPort).shouldHaveNoMoreInteractions();
  }

  @Test
  @DisplayName(
      "deadline refund recovery 전에 order가 DEADLINE_REFUNDED이면 새 refund intent 없이 DEADLINE_REFUNDED로 동기화한다")
  void deadlineRefund_recovery_syncs_deadlineRefunded_order_without_new_intent() {
    Reservation reservation = reservation(ReservationStatus.DEADLINE_REFUND_PENDING);
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(order(ReservationEscrowOrderView.STATE_DEADLINE_REFUNDED, 1_900_000_000L));

    RecoverReservationEscrowResult result =
        sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID));

    assertThat(result.status()).isEqualTo(ReservationStatus.DEADLINE_REFUNDED);
    assertThat(result.escrowStatus()).isEqualTo(ReservationEscrowStatus.DEADLINE_REFUNDED.name());
    assertThat(result.web3()).isNull();
    then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("purchase recovery에서 order가 없으면 같은 orderKey로 새 signable intent를 준비한다")
  void purchase_recovery_recreates_intent_when_order_absent() {
    Reservation reservation = reservation(ReservationStatus.PURCHASE_PENDING);
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(order(ReservationEscrowOrderView.STATE_ABSENT, 0L));
    given(loadReservationEscrowPaymentConfigPort.load())
        .willReturn(
            new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                "0x3333333333333333333333333333333333333333", 18, 2_592_000L));
    given(prepareReservationEscrowExecutionPort.preparePurchase(any()))
        .willReturn(new PrepareReservationEscrowResult(web3()));

    RecoverReservationEscrowResult result =
        sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID));

    assertThat(result.status()).isEqualTo(ReservationStatus.PURCHASE_PENDING);
    assertThat(result.web3()).isNotNull();
    ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(reservationCaptor.capture());
    assertThat(reservationCaptor.getValue().getCurrentExecutionIntentPublicId())
        .isEqualTo("intent-1");
  }

  @Test
  @DisplayName("recovery Phase B bind 실패 시 signable intent를 취소하고 local purchase hold를 종료한다")
  void recovery_phaseB_bindFailure_cancelsIntentAndMarksPaymentFailed() {
    Reservation reservation = reservation(ReservationStatus.PURCHASE_PENDING);
    Reservation changed = reservation.toBuilder().status(ReservationStatus.PENDING).build();
    AtomicReference<Reservation> latestSaved = new AtomicReference<>();
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation))
        .willReturn(Optional.of(reservation))
        .willReturn(Optional.of(changed))
        .willReturn(Optional.of(reservation));
    given(saveReservationPort.save(any()))
        .willAnswer(
            invocation -> {
              Reservation saved = invocation.getArgument(0, Reservation.class);
              latestSaved.set(saved);
              return saved;
            });
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(order(ReservationEscrowOrderView.STATE_ABSENT, 0L));
    given(loadReservationEscrowPaymentConfigPort.load())
        .willReturn(
            new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                "0x3333333333333333333333333333333333333333", 18, 2_592_000L));
    given(prepareReservationEscrowExecutionPort.preparePurchase(any()))
        .willReturn(new PrepareReservationEscrowResult(web3()));
    given(cancelReservationEscrowExecutionPort.cancelSignableIntent(any(), any(), any()))
        .willReturn(true);

    assertThatThrownBy(
            () -> sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getCode())
                    .isEqualTo(ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT.getCode()));

    then(cancelReservationEscrowExecutionPort)
        .should()
        .cancelSignableIntent(eq("intent-1"), eq("MARKETPLACE_RECOVERY_BIND_FAILED"), any());
    assertThat(latestSaved.get().getStatus()).isEqualTo(ReservationStatus.PAYMENT_FAILED);
    assertThat(latestSaved.get().getCurrentExecutionIntentPublicId()).isNull();
  }

  private Reservation reservation(ReservationStatus status) {
    return Reservation.builder()
        .id(RESERVATION_ID)
        .userId(BUYER_ID)
        .trainerId(TRAINER_ID)
        .slotId(99L)
        .reservationDate(LocalDate.of(2024, 6, 10))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(status)
        .escrowStatus(ReservationEscrowStatus.PURCHASE_PENDING)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .orderId("123e4567-e89b-12d3-a456-426614174000")
        .orderKey(ORDER_KEY)
        .buyerWalletAddress("0x1111111111111111111111111111111111111111")
        .trainerWalletAddress("0x2222222222222222222222222222222222222222")
        .tokenAddress("0x3333333333333333333333333333333333333333")
        .priceBaseUnits("50000000000000000000000")
        .bookedPriceAmount(50_000)
        .version(1L)
        .build();
  }

  private ReservationEscrowOrderView order(int state, Long deadlineEpochSeconds) {
    return new ReservationEscrowOrderView(
        ORDER_KEY,
        "50000000000000000000000",
        "0x3333333333333333333333333333333333333333",
        deadlineEpochSeconds,
        state,
        state == ReservationEscrowOrderView.STATE_ABSENT
            ? "0x0000000000000000000000000000000000000000"
            : "0x1111111111111111111111111111111111111111",
        "0x2222222222222222222222222222222222222222");
  }

  private ReservationExecutionWriteView web3() {
    return new ReservationExecutionWriteView(
        new ReservationExecutionWriteView.Resource(
            "ORDER", String.valueOf(RESERVATION_ID), "PENDING"),
        "MARKETPLACE_CLASS_PURCHASE",
        ORDER_KEY,
        new ReservationExecutionWriteView.ExecutionIntent(
            "intent-1", "AWAITING_SIGNATURE", LocalDateTime.now(FIXED_CLOCK).plusMinutes(5), 300L),
        new ReservationExecutionWriteView.Execution("EIP7702", 1),
        null,
        null,
        false,
        null,
        null);
  }
}
