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
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionStateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionWritePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RecordTrainerStrikePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.ReplayConfirmedReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.TrainerStrikeEvent;
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
  private static final Long OTHER_USER_ID = 999L;
  private static final String ORDER_KEY = "0x" + "0".repeat(63) + "1";

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort;
  @Mock private CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort;
  @Mock private LoadReservationExecutionWritePort loadReservationExecutionWritePort;
  @Mock private LoadReservationExecutionStatePort loadReservationExecutionStatePort;
  @Mock private ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort;
  @Mock private LoadReservationWalletPort loadReservationWalletPort;
  @Mock private LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort;
  @Mock private LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;
  @Mock private RecordTrainerStrikePort recordTrainerStrikePort;

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
            loadReservationExecutionStatePort,
            replayConfirmedReservationExecutionPort,
            loadReservationWalletPort,
            loadReservationEscrowPaymentConfigPort,
            loadReservationEscrowOrderPort,
            recordTrainerStrikePort,
            FIXED_CLOCK);
    org.mockito.Mockito.lenient()
        .when(saveReservationPort.save(any()))
        .thenAnswer(invocation -> invocation.getArgument(0, Reservation.class));
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
  @DisplayName("actor evidence 없는 CANCELLED order는 buyer cancel로 확정하지 않고 manual sync로 둔다")
  void recovery_cancelledOrderWithoutActorEvidence_marksManualSyncRequired() {
    Reservation reservation = reservation(ReservationStatus.PURCHASE_PENDING);
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation))
        .willReturn(Optional.of(reservation));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(order(ReservationEscrowOrderView.STATE_CANCELLED, 1_900_000_000L));

    RecoverReservationEscrowResult result =
        sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID));

    assertThat(result.status()).isEqualTo(ReservationStatus.MANUAL_SYNC_REQUIRED);
    assertThat(result.escrowStatus())
        .isEqualTo(ReservationEscrowStatus.MANUAL_SYNC_REQUIRED.name());
    assertThat(result.web3()).isNull();
    then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("deadline 이후 cancel pending recovery는 cancel 대신 deadline refund intent를 준비한다")
  void expired_cancelPending_recovery_preparesDeadlineRefundIntent() {
    long deadlineEpochSeconds = FIXED_CLOCK.instant().minusSeconds(60).getEpochSecond();
    Reservation reservation =
        reservation(ReservationStatus.CANCEL_PENDING).toBuilder()
            .escrowStatus(ReservationEscrowStatus.CANCEL_PENDING)
            .contractDeadlineEpochSeconds(deadlineEpochSeconds)
            .contractDeadlineAt(LocalDateTime.now(FIXED_CLOCK).minusMinutes(1))
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
        .willReturn(new PrepareReservationEscrowResult(web3("MARKETPLACE_CLASS_EXPIRED_REFUND")));

    RecoverReservationEscrowResult result =
        sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID));

    assertThat(result.status()).isEqualTo(ReservationStatus.DEADLINE_REFUND_PENDING);
    assertThat(result.web3()).isNotNull();
    then(loadReservationExecutionWritePort).shouldHaveNoInteractions();
    then(prepareReservationEscrowExecutionPort).should().prepareDeadlineRefund(any());
    then(prepareReservationEscrowExecutionPort).shouldHaveNoMoreInteractions();
  }

  @Test
  @DisplayName("deadline 이후 trainer reject recovery는 저장 전에 refund required로 거부한다")
  void expired_rejectPending_recovery_rejectsBeforeMutatingReservation() {
    long deadlineEpochSeconds = FIXED_CLOCK.instant().minusSeconds(60).getEpochSecond();
    Reservation reservation =
        reservation(ReservationStatus.REJECT_PENDING).toBuilder()
            .escrowStatus(ReservationEscrowStatus.REJECT_PENDING)
            .contractDeadlineEpochSeconds(deadlineEpochSeconds)
            .contractDeadlineAt(LocalDateTime.now(FIXED_CLOCK).minusMinutes(1))
            .pendingAction(ReservationEscrowAction.TRAINER_REJECT)
            .pendingAttemptToken("reject-token")
            .priorStatus(ReservationStatus.PENDING)
            .priorEscrowStatus(ReservationEscrowStatus.LOCKED)
            .build();
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation));

    assertThatThrownBy(
            () -> sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, TRAINER_ID)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getCode())
                    .isEqualTo(ErrorCode.MARKETPLACE_DEADLINE_REFUND_REQUIRED.getCode()));

    then(saveReservationPort).shouldHaveNoInteractions();
    then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("deadline 이후 cancel pending recovery는 비소유자 요청에서 refund available 저장 전에 거부한다")
  void expiredCancelPending_nonOwnerRecovery_rejectsBeforeMutatingReservation() {
    Reservation reservation =
        reservation(ReservationStatus.CANCEL_PENDING).toBuilder()
            .escrowStatus(ReservationEscrowStatus.CANCEL_PENDING)
            .contractDeadlineEpochSeconds(FIXED_CLOCK.instant().minusSeconds(60).getEpochSecond())
            .contractDeadlineAt(LocalDateTime.now(FIXED_CLOCK).minusMinutes(1))
            .pendingAction(ReservationEscrowAction.BUYER_CANCEL)
            .pendingAttemptToken("cancel-token")
            .priorStatus(ReservationStatus.PENDING)
            .priorEscrowStatus(ReservationEscrowStatus.LOCKED)
            .build();
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation));

    assertThatThrownBy(
            () -> sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, OTHER_USER_ID)))
        .isInstanceOf(RuntimeException.class);

    then(saveReservationPort).shouldHaveNoInteractions();
    then(loadReservationEscrowOrderPort).shouldHaveNoInteractions();
    then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("current execution intent가 CONFIRMED이면 replay 후 최신 reservation 상태를 반환한다")
  void recovery_currentConfirmedIntent_replaysConfirmedHookAndReturnsLatestReservation() {
    Reservation reservation =
        reservation(ReservationStatus.PURCHASE_PENDING).toBuilder()
            .currentExecutionIntentPublicId("intent-1")
            .build();
    Reservation repaired =
        reservation.markPurchaseConfirmedLocked(
            1_900_000_000L, LocalDateTime.of(2030, 3, 17, 17, 46, 40));
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation));
    given(loadReservationExecutionStatePort.loadState("intent-1"))
        .willReturn(state("MARKETPLACE_CLASS_PURCHASE", "CONFIRMED", "intent-1", BUYER_ID));
    given(loadReservationPort.findById(RESERVATION_ID)).willReturn(Optional.of(repaired));

    RecoverReservationEscrowResult result =
        sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID));

    assertThat(result.status()).isEqualTo(ReservationStatus.PENDING);
    assertThat(result.web3()).isNull();
    then(replayConfirmedReservationExecutionPort)
        .should()
        .replayConfirmed("intent-1", "MARKETPLACE_CLASS_PURCHASE");
    then(loadReservationExecutionWritePort).shouldHaveNoInteractions();
    then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("transaction이 SUCCEEDED인 current intent는 intent 상태가 pending이어도 replay repair를 실행한다")
  void recovery_currentSucceededTransaction_replaysConfirmedRepairAndReturnsLatestReservation() {
    Reservation reservation =
        reservation(ReservationStatus.PURCHASE_PENDING).toBuilder()
            .currentExecutionIntentPublicId("intent-1")
            .build();
    Reservation repaired =
        reservation.markPurchaseConfirmedLocked(
            1_900_000_000L, LocalDateTime.of(2030, 3, 17, 17, 46, 40));
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation));
    given(loadReservationExecutionStatePort.loadState("intent-1"))
        .willReturn(
            state(
                "MARKETPLACE_CLASS_PURCHASE",
                "PENDING_ONCHAIN",
                "intent-1",
                BUYER_ID,
                "SUCCEEDED"));
    given(loadReservationPort.findById(RESERVATION_ID)).willReturn(Optional.of(repaired));

    RecoverReservationEscrowResult result =
        sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID));

    assertThat(result.status()).isEqualTo(ReservationStatus.PENDING);
    assertThat(result.web3()).isNull();
    then(replayConfirmedReservationExecutionPort)
        .should()
        .replayConfirmed("intent-1", "MARKETPLACE_CLASS_PURCHASE");
    then(loadReservationExecutionWritePort).shouldHaveNoInteractions();
    then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("trainer-owned confirmed reject intent는 buyer recover에서도 replay로 수렴한다")
  void recovery_trainerOwnedConfirmedRejectIntent_replaysForBuyerRecovery() {
    Reservation reservation =
        reservation(ReservationStatus.REJECT_PENDING).toBuilder()
            .currentExecutionIntentPublicId("reject-intent-1")
            .build();
    Reservation repaired =
        reservation.syncChainOutcome(
            ReservationStatus.REJECTED,
            ReservationEscrowStatus.REFUNDED,
            "reject-tx",
            reservation.getContractDeadlineEpochSeconds(),
            reservation.getContractDeadlineAt());
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation));
    given(loadReservationExecutionStatePort.loadState("reject-intent-1"))
        .willReturn(state("MARKETPLACE_CLASS_CANCEL", "CONFIRMED", "reject-intent-1", TRAINER_ID));
    given(loadReservationPort.findById(RESERVATION_ID)).willReturn(Optional.of(repaired));

    RecoverReservationEscrowResult result =
        sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID));

    assertThat(result.status()).isEqualTo(ReservationStatus.REJECTED);
    assertThat(result.web3()).isNull();
    then(replayConfirmedReservationExecutionPort)
        .should()
        .replayConfirmed("reject-intent-1", "MARKETPLACE_CLASS_CANCEL");
    then(loadReservationExecutionWritePort).shouldHaveNoInteractions();
    then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("current execution intent가 CONFIRMED여도 비참여자는 replay를 실행할 수 없다")
  void recovery_currentConfirmedIntent_nonParticipantRejectedBeforeReplay() {
    Reservation reservation =
        reservation(ReservationStatus.PURCHASE_PENDING).toBuilder()
            .currentExecutionIntentPublicId("intent-1")
            .build();
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation));
    given(loadReservationExecutionStatePort.loadState("intent-1"))
        .willReturn(state("MARKETPLACE_CLASS_PURCHASE", "CONFIRMED", "intent-1", BUYER_ID));

    assertThatThrownBy(
            () -> sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, OTHER_USER_ID)))
        .isInstanceOf(MarketplaceUnauthorizedAccessException.class);

    then(replayConfirmedReservationExecutionPort).shouldHaveNoInteractions();
    then(loadReservationPort).should().findByIdWithLock(RESERVATION_ID);
    then(loadReservationPort).shouldHaveNoMoreInteractions();
    then(loadReservationExecutionWritePort).shouldHaveNoInteractions();
    then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("deadline이 지났어도 current intent가 진행 중이면 refund 전환 전에 기존 intent를 반환한다")
  void expiredCancelPending_currentAwaitingSignature_returnsExistingIntentWithoutClearingPending() {
    Reservation reservation =
        reservation(ReservationStatus.CANCEL_PENDING).toBuilder()
            .escrowStatus(ReservationEscrowStatus.CANCEL_PENDING)
            .contractDeadlineEpochSeconds(FIXED_CLOCK.instant().minusSeconds(60).getEpochSecond())
            .contractDeadlineAt(LocalDateTime.now(FIXED_CLOCK).minusMinutes(1))
            .pendingAction(ReservationEscrowAction.BUYER_CANCEL)
            .pendingAttemptToken("cancel-token")
            .currentExecutionIntentPublicId("cancel-intent-1")
            .priorStatus(ReservationStatus.PENDING)
            .priorEscrowStatus(ReservationEscrowStatus.LOCKED)
            .build();
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation));
    given(loadReservationExecutionStatePort.loadState("cancel-intent-1"))
        .willReturn(
            state("MARKETPLACE_CLASS_CANCEL", "AWAITING_SIGNATURE", "cancel-intent-1", BUYER_ID));
    given(loadReservationExecutionWritePort.load(BUYER_ID, "cancel-intent-1"))
        .willReturn(web3("MARKETPLACE_CLASS_CANCEL", "AWAITING_SIGNATURE"));

    RecoverReservationEscrowResult result =
        sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID));

    assertThat(result.status()).isEqualTo(ReservationStatus.CANCEL_PENDING);
    assertThat(result.web3()).isNotNull();
    assertThat(result.web3().existing()).isTrue();
    assertThat(result.web3().executionIntent().status()).isEqualTo("AWAITING_SIGNATURE");
    then(saveReservationPort).shouldHaveNoInteractions();
    then(loadReservationEscrowOrderPort).shouldHaveNoInteractions();
    then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
    then(replayConfirmedReservationExecutionPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("deadline이 지난 current intent가 retryable terminal이면 refund-only recovery로 전환한다")
  void expiredCancelPending_currentFailedOnchain_preparesDeadlineRefundIntent() {
    long deadlineEpochSeconds = FIXED_CLOCK.instant().minusSeconds(60).getEpochSecond();
    Reservation reservation =
        reservation(ReservationStatus.CANCEL_PENDING).toBuilder()
            .escrowStatus(ReservationEscrowStatus.CANCEL_PENDING)
            .contractDeadlineEpochSeconds(deadlineEpochSeconds)
            .contractDeadlineAt(LocalDateTime.now(FIXED_CLOCK).minusMinutes(1))
            .pendingAction(ReservationEscrowAction.BUYER_CANCEL)
            .pendingAttemptToken("cancel-token")
            .currentExecutionIntentPublicId("cancel-intent-1")
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
        .willAnswer(invocation -> Optional.of(latestSaved.get()))
        .willAnswer(invocation -> Optional.of(latestSaved.get()))
        .willAnswer(invocation -> Optional.of(latestSaved.get()));
    given(loadReservationExecutionStatePort.loadState("cancel-intent-1"))
        .willReturn(
            state("MARKETPLACE_CLASS_CANCEL", "FAILED_ONCHAIN", "cancel-intent-1", BUYER_ID));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(order(ReservationEscrowOrderView.STATE_CREATED, deadlineEpochSeconds));
    given(loadReservationEscrowPaymentConfigPort.load())
        .willReturn(
            new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                "0x3333333333333333333333333333333333333333", 18, 2_592_000L));
    given(prepareReservationEscrowExecutionPort.prepareDeadlineRefund(any()))
        .willReturn(
            new PrepareReservationEscrowResult(
                web3("MARKETPLACE_CLASS_EXPIRED_REFUND", "AWAITING_SIGNATURE", "refund-intent-1")));

    RecoverReservationEscrowResult result =
        sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID));

    assertThat(result.status()).isEqualTo(ReservationStatus.DEADLINE_REFUND_PENDING);
    assertThat(result.web3()).isNotNull();
    assertThat(latestSaved.get().getCurrentExecutionIntentPublicId()).isEqualTo("refund-intent-1");
    then(prepareReservationEscrowExecutionPort).should().prepareDeadlineRefund(any());
    then(prepareReservationEscrowExecutionPort).shouldHaveNoMoreInteractions();
    then(loadReservationExecutionWritePort).shouldHaveNoInteractions();
    then(replayConfirmedReservationExecutionPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName(
      "expired REJECT_PENDING의 trainer-owned retryable intent는 buyer deadline refund recovery로 라우팅한다")
  void expiredRejectPending_trainerOwnedRetryableTerminal_buyerRecoveryPreparesDeadlineRefund() {
    long deadlineEpochSeconds = FIXED_CLOCK.instant().minusSeconds(60).getEpochSecond();
    Reservation reservation =
        reservation(ReservationStatus.REJECT_PENDING).toBuilder()
            .escrowStatus(ReservationEscrowStatus.REJECT_PENDING)
            .contractDeadlineEpochSeconds(deadlineEpochSeconds)
            .contractDeadlineAt(LocalDateTime.now(FIXED_CLOCK).minusMinutes(1))
            .pendingAction(ReservationEscrowAction.TRAINER_REJECT)
            .pendingAttemptToken("reject-token")
            .currentExecutionIntentPublicId("reject-intent-1")
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
        .willAnswer(invocation -> Optional.of(latestSaved.get()))
        .willAnswer(invocation -> Optional.of(latestSaved.get()))
        .willAnswer(invocation -> Optional.of(latestSaved.get()));
    given(loadReservationExecutionStatePort.loadState("reject-intent-1"))
        .willReturn(
            state("MARKETPLACE_CLASS_CANCEL", "FAILED_ONCHAIN", "reject-intent-1", TRAINER_ID));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(order(ReservationEscrowOrderView.STATE_CREATED, deadlineEpochSeconds));
    given(loadReservationEscrowPaymentConfigPort.load())
        .willReturn(
            new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                "0x3333333333333333333333333333333333333333", 18, 2_592_000L));
    given(prepareReservationEscrowExecutionPort.prepareDeadlineRefund(any()))
        .willReturn(
            new PrepareReservationEscrowResult(
                web3("MARKETPLACE_CLASS_EXPIRED_REFUND", "AWAITING_SIGNATURE", "refund-intent-1")));

    RecoverReservationEscrowResult result =
        sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID));

    assertThat(result.status()).isEqualTo(ReservationStatus.DEADLINE_REFUND_PENDING);
    assertThat(result.web3()).isNotNull();
    assertThat(latestSaved.get().getCurrentExecutionIntentPublicId()).isEqualTo("refund-intent-1");
    then(loadReservationExecutionWritePort).shouldHaveNoInteractions();
    then(prepareReservationEscrowExecutionPort).should().prepareDeadlineRefund(any());
  }

  @Test
  @DisplayName(
      "expired REJECT_PENDING의 trainer-owned active intent는 buyer recovery에서 pointer를 지우지 않고 conflict로 막는다")
  void expiredRejectPending_trainerOwnedActiveIntent_buyerRecoveryActiveConflict() {
    long deadlineEpochSeconds = FIXED_CLOCK.instant().minusSeconds(60).getEpochSecond();
    Reservation reservation =
        reservation(ReservationStatus.REJECT_PENDING).toBuilder()
            .escrowStatus(ReservationEscrowStatus.REJECT_PENDING)
            .contractDeadlineEpochSeconds(deadlineEpochSeconds)
            .contractDeadlineAt(LocalDateTime.now(FIXED_CLOCK).minusMinutes(1))
            .pendingAction(ReservationEscrowAction.TRAINER_REJECT)
            .pendingAttemptToken("reject-token")
            .currentExecutionIntentPublicId("reject-intent-1")
            .priorStatus(ReservationStatus.PENDING)
            .priorEscrowStatus(ReservationEscrowStatus.LOCKED)
            .build();
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation));
    given(loadReservationExecutionStatePort.loadState("reject-intent-1"))
        .willReturn(
            state("MARKETPLACE_CLASS_CANCEL", "AWAITING_SIGNATURE", "reject-intent-1", TRAINER_ID));

    assertThatThrownBy(
            () -> sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getCode())
                    .isEqualTo(ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT.getCode()));

    then(saveReservationPort).shouldHaveNoInteractions();
    then(loadReservationExecutionWritePort).shouldHaveNoInteractions();
    then(loadReservationEscrowOrderPort).shouldHaveNoInteractions();
    then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("unbound DEADLINE_REFUND_PENDING recovery는 pending token을 교체하지 않고 conflict로 막는다")
  void unboundDeadlineRefundPending_recoveryActiveConflict() {
    Reservation reservation =
        reservation(ReservationStatus.DEADLINE_REFUND_PENDING).toBuilder()
            .escrowStatus(ReservationEscrowStatus.DEADLINE_REFUND_PENDING)
            .contractDeadlineEpochSeconds(FIXED_CLOCK.instant().minusSeconds(60).getEpochSecond())
            .contractDeadlineAt(LocalDateTime.now(FIXED_CLOCK).minusMinutes(1))
            .pendingAction(ReservationEscrowAction.DEADLINE_REFUND)
            .pendingAttemptToken("refund-token")
            .currentExecutionIntentPublicId(null)
            .priorStatus(ReservationStatus.DEADLINE_REFUND_AVAILABLE)
            .priorEscrowStatus(ReservationEscrowStatus.DEADLINE_REFUND_AVAILABLE)
            .build();
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(
            order(
                ReservationEscrowOrderView.STATE_CREATED,
                FIXED_CLOCK.instant().minusSeconds(60).getEpochSecond()));

    assertThatThrownBy(
            () -> sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getCode())
                    .isEqualTo(ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT.getCode()));

    then(loadReservationEscrowOrderPort).should().getOrder(ORDER_KEY);
    then(saveReservationPort).shouldHaveNoInteractions();
    then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("trainer reject recovery chain CANCELLED sync는 REJECTED와 strike를 함께 기록한다")
  void trainerRejectRecovery_cancelledOrder_recordsStrike() {
    Reservation reservation =
        reservation(ReservationStatus.REJECT_PENDING).toBuilder()
            .escrowStatus(ReservationEscrowStatus.REJECT_PENDING)
            .contractDeadlineEpochSeconds(FIXED_CLOCK.instant().plusSeconds(600).getEpochSecond())
            .contractDeadlineAt(LocalDateTime.now(FIXED_CLOCK).plusMinutes(10))
            .pendingAction(ReservationEscrowAction.TRAINER_REJECT)
            .pendingAttemptToken("reject-token")
            .priorStatus(ReservationStatus.PENDING)
            .priorEscrowStatus(ReservationEscrowStatus.LOCKED)
            .build();
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation))
        .willReturn(Optional.of(reservation));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(
            order(
                ReservationEscrowOrderView.STATE_CANCELLED,
                FIXED_CLOCK.instant().plusSeconds(600).getEpochSecond()));

    RecoverReservationEscrowResult result =
        sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, TRAINER_ID));

    assertThat(result.status()).isEqualTo(ReservationStatus.REJECTED);
    then(recordTrainerStrikePort)
        .should()
        .recordStrike(
            TRAINER_ID,
            TrainerStrikeEvent.REASON_REJECT,
            RecordTrainerStrikePort.SOURCE_MARKETPLACE_RESERVATION_REJECT,
            String.valueOf(RESERVATION_ID));
    then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("trainer reject recovery chain sync 후 strike 실패는 reservation 결과를 깨지 않는다")
  void trainerRejectRecovery_cancelledOrder_strikeFailureDoesNotFailSync() {
    Reservation reservation =
        reservation(ReservationStatus.REJECT_PENDING).toBuilder()
            .escrowStatus(ReservationEscrowStatus.REJECT_PENDING)
            .contractDeadlineEpochSeconds(FIXED_CLOCK.instant().plusSeconds(600).getEpochSecond())
            .contractDeadlineAt(LocalDateTime.now(FIXED_CLOCK).plusMinutes(10))
            .pendingAction(ReservationEscrowAction.TRAINER_REJECT)
            .pendingAttemptToken("reject-token")
            .priorStatus(ReservationStatus.PENDING)
            .priorEscrowStatus(ReservationEscrowStatus.LOCKED)
            .build();
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation))
        .willReturn(Optional.of(reservation));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(
            order(
                ReservationEscrowOrderView.STATE_CANCELLED,
                FIXED_CLOCK.instant().plusSeconds(600).getEpochSecond()));
    org.mockito.Mockito.doThrow(new IllegalStateException("strike failed"))
        .when(recordTrainerStrikePort)
        .recordStrike(
            TRAINER_ID,
            TrainerStrikeEvent.REASON_REJECT,
            RecordTrainerStrikePort.SOURCE_MARKETPLACE_RESERVATION_REJECT,
            String.valueOf(RESERVATION_ID));

    RecoverReservationEscrowResult result =
        sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, TRAINER_ID));

    assertThat(result.status()).isEqualTo(ReservationStatus.REJECTED);
    then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
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
  @DisplayName("deadline refund recovery에서 prepare 실패 시 신규 pending 전환을 available 상태로 롤백한다")
  void deadlineRefund_recovery_prepareFailure_rollsBackNewPendingTransition() {
    long deadlineEpochSeconds = FIXED_CLOCK.instant().minusSeconds(60).getEpochSecond();
    Reservation reservation =
        reservation(ReservationStatus.DEADLINE_REFUND_AVAILABLE).toBuilder()
            .escrowStatus(ReservationEscrowStatus.DEADLINE_REFUND_AVAILABLE)
            .contractDeadlineEpochSeconds(deadlineEpochSeconds)
            .contractDeadlineAt(LocalDateTime.now(FIXED_CLOCK).minusMinutes(1))
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
        .willAnswer(invocation -> Optional.of(latestSaved.get()))
        .willAnswer(invocation -> Optional.of(latestSaved.get()));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(order(ReservationEscrowOrderView.STATE_CREATED, deadlineEpochSeconds));
    given(loadReservationEscrowPaymentConfigPort.load())
        .willReturn(
            new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                "0x3333333333333333333333333333333333333333", 18, 2_592_000L));
    given(prepareReservationEscrowExecutionPort.prepareDeadlineRefund(any()))
        .willThrow(new BusinessException(ErrorCode.MARKETPLACE_DEADLINE_SYNC_REQUIRED));

    assertThatThrownBy(
            () -> sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getCode())
                    .isEqualTo(ErrorCode.MARKETPLACE_DEADLINE_SYNC_REQUIRED.getCode()));

    assertThat(latestSaved.get().getStatus())
        .isEqualTo(ReservationStatus.DEADLINE_REFUND_AVAILABLE);
    assertThat(latestSaved.get().getCurrentExecutionIntentPublicId()).isNull();
    then(prepareReservationEscrowExecutionPort).should().prepareDeadlineRefund(any());
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
  @DisplayName("non-purchase recovery에서 order가 없으면 새 intent 대신 sync required로 중단한다")
  void nonPurchase_recovery_absentOrder_throwsDeadlineSyncRequired() {
    Reservation reservation =
        reservation(ReservationStatus.CANCEL_PENDING).toBuilder()
            .escrowStatus(ReservationEscrowStatus.CANCEL_PENDING)
            .contractDeadlineAt(LocalDateTime.now(FIXED_CLOCK).plusMinutes(10))
            .contractDeadlineEpochSeconds(FIXED_CLOCK.instant().plusSeconds(600).getEpochSecond())
            .pendingAction(ReservationEscrowAction.BUYER_CANCEL)
            .pendingAttemptToken("cancel-token")
            .priorStatus(ReservationStatus.PENDING)
            .priorEscrowStatus(ReservationEscrowStatus.LOCKED)
            .build();
    given(loadReservationPort.findByIdWithLock(RESERVATION_ID))
        .willReturn(Optional.of(reservation))
        .willReturn(Optional.of(reservation));
    given(loadReservationEscrowOrderPort.getOrder(ORDER_KEY))
        .willReturn(order(ReservationEscrowOrderView.STATE_ABSENT, 0L));

    assertThatThrownBy(
            () -> sut.execute(new RecoverReservationEscrowCommand(RESERVATION_ID, BUYER_ID)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getCode())
                    .isEqualTo(ErrorCode.MARKETPLACE_DEADLINE_SYNC_REQUIRED.getCode()));

    then(prepareReservationEscrowExecutionPort).shouldHaveNoInteractions();
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
    return web3("MARKETPLACE_CLASS_PURCHASE");
  }

  private ReservationExecutionWriteView web3(String actionType) {
    return web3(actionType, "AWAITING_SIGNATURE");
  }

  private ReservationExecutionWriteView web3(String actionType, String intentStatus) {
    return web3(actionType, intentStatus, "intent-1");
  }

  private ReservationExecutionWriteView web3(
      String actionType, String intentStatus, String intentId) {
    return new ReservationExecutionWriteView(
        new ReservationExecutionWriteView.Resource(
            "ORDER", String.valueOf(RESERVATION_ID), "PENDING"),
        actionType,
        ORDER_KEY,
        new ReservationExecutionWriteView.ExecutionIntent(
            intentId, intentStatus, LocalDateTime.now(FIXED_CLOCK).plusMinutes(5), 300L),
        new ReservationExecutionWriteView.Execution("EIP7702", 1),
        null,
        null,
        false,
        null,
        null);
  }

  private ReservationExecutionStateView state(
      String actionType, String intentStatus, String intentId, Long requesterUserId) {
    return new ReservationExecutionStateView(intentId, intentStatus, actionType, requesterUserId);
  }

  private ReservationExecutionStateView state(
      String actionType,
      String intentStatus,
      String intentId,
      Long requesterUserId,
      String transactionStatus) {
    return new ReservationExecutionStateView(
        intentId, intentStatus, actionType, requesterUserId, 99L, transactionStatus, "0xhash");
  }
}
