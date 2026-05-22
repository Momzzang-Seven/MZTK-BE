package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionConfirmedCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionTerminatedCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionTerminatedCommand.ReservationEscrowExecutionTerminationEvidence;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RecordTrainerStrikePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationPostCommitPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationEscrow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.ReservationCreateIdempotency;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationCreateIdempotencyStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowActorType;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationTerminalResolvedBy;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.TrainerStrikeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplyReservationEscrowExecutionHookServiceTest {

  private static final long CONTRACT_DEADLINE_EPOCH_SECONDS =
      Instant.parse("2026-05-22T11:00:00Z").getEpochSecond();

  @Mock private LoadReservationPort loadReservationPort;
  @Mock private SaveReservationPort saveReservationPort;
  @Mock private LoadReservationActionStatePort loadReservationActionStatePort;
  @Mock private SaveReservationActionStatePort saveReservationActionStatePort;
  @Mock private LoadReservationEscrowPort loadReservationEscrowPort;
  @Mock private SaveReservationEscrowPort saveReservationEscrowPort;
  @Mock private LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;
  @Mock private RecordTrainerStrikePort recordTrainerStrikePort;
  @Mock private LoadReservationCreateIdempotencyPort loadReservationCreateIdempotencyPort;
  @Mock private SaveReservationCreateIdempotencyPort saveReservationCreateIdempotencyPort;

  private ApplyReservationEscrowExecutionHookService service;

  @BeforeEach
  void setUp() {
    service =
        new ApplyReservationEscrowExecutionHookService(
            loadReservationPort,
            saveReservationPort,
            Clock.fixed(Instant.parse("2026-05-16T00:00:00Z"), ZoneOffset.UTC),
            recordTrainerStrikePort,
            loadReservationEscrowOrderPort,
            loadReservationCreateIdempotencyPort,
            saveReservationCreateIdempotencyPort);
    service.setTransactionPort(ReservationTestTransactionPort.direct());
    service.setActionStatePorts(loadReservationActionStatePort, saveReservationActionStatePort);
    service.setEscrowProjectionPorts(loadReservationEscrowPort, saveReservationEscrowPort);
  }

  @ParameterizedTest
  @MethodSource("purchaseConfirmedChainStates")
  void confirmedPurchaseHook_syncsOnChainOrderState(
      int chainState,
      ReservationStatus expectedStatus,
      ReservationEscrowStatus expectedEscrowStatus) {
    Reservation reservation = pendingPurchaseReservation();
    given(loadReservationEscrowOrderPort.getOrder(reservation.getOrderKey()))
        .willReturn(orderView(chainState));
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(purchaseActionState()));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));

    service.afterExecutionConfirmed(purchaseConfirmedCommand(reservation));

    ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(reservationCaptor.capture());
    assertThat(reservationCaptor.getValue().getStatus()).isEqualTo(expectedStatus);
    assertThat(reservationCaptor.getValue().getEffectiveEscrowStatus())
        .isEqualTo(expectedEscrowStatus);
  }

  @Test
  void confirmedPurchaseHook_marksDeadlineSyncRequiredWhenOrderIsAbsent() {
    Reservation reservation = pendingPurchaseReservation();
    given(loadReservationEscrowOrderPort.getOrder(reservation.getOrderKey()))
        .willReturn(
            new ReservationEscrowOrderView(
                reservation.getOrderKey(),
                "50000",
                "0x3333333333333333333333333333333333333333",
                1_800L,
                ReservationEscrowOrderView.STATE_ABSENT,
                "0x0000000000000000000000000000000000000000",
                "0x2222222222222222222222222222222222222222"));
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(purchaseActionState()));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));

    service.afterExecutionConfirmed(purchaseConfirmedCommand(reservation));

    ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(reservationCaptor.capture());
    assertThat(reservationCaptor.getValue().getStatus())
        .isEqualTo(ReservationStatus.DEADLINE_SYNC_REQUIRED);
    assertThat(reservationCaptor.getValue().getEffectiveEscrowStatus())
        .isEqualTo(ReservationEscrowStatus.DEADLINE_SYNC_REQUIRED);
  }

  @Test
  void confirmedHook_marksMatchingActionStateConfirmed() {
    Reservation reservation = pendingCancelReservation();
    MarketplaceReservationActionState actionState = activeActionState();
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-1"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-1"))
        .willReturn(Optional.of(actionState));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));

    service.afterExecutionConfirmed(
        new ReservationEscrowExecutionConfirmedCommand(
            "intent-1",
            "0xtx",
            "MARKETPLACE_CLASS_CANCEL",
            "BUYER",
            reservation.getId(),
            reservation.getOrderKey(),
            null,
            null,
            reservation.sessionEndAt(),
            "attempt-1",
            20L));

    ArgumentCaptor<MarketplaceReservationActionState> captor =
        ArgumentCaptor.forClass(MarketplaceReservationActionState.class);
    then(saveReservationActionStatePort).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ReservationActionStateStatus.CONFIRMED);
    assertThat(captor.getValue().getRetryable()).isFalse();
    ArgumentCaptor<MarketplaceReservationEscrow> escrowCaptor =
        ArgumentCaptor.forClass(MarketplaceReservationEscrow.class);
    then(saveReservationEscrowPort).should().save(escrowCaptor.capture());
    assertThat(escrowCaptor.getValue().getEscrowStatus())
        .isEqualTo(ReservationEscrowStatus.REFUNDED);
    assertThat(escrowCaptor.getValue().getLastTxHash()).isEqualTo("0xtx");
  }

  @ParameterizedTest
  @MethodSource("confirmedNonPurchaseActions")
  void confirmedHook_syncsEachNonPurchaseAction(
      String actionType,
      String actorType,
      ReservationEscrowAction action,
      ReservationEscrowActorType actor,
      Reservation reservation,
      ReservationStatus expectedStatus,
      ReservationEscrowStatus expectedEscrowStatus,
      boolean strikeExpected) {
    MarketplaceReservationActionState actionState =
        activeActionState(action, actor, actor == ReservationEscrowActorType.TRAINER ? 9L : 7L);
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-action"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-action"))
        .willReturn(Optional.of(actionState));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));

    service.afterExecutionConfirmed(
        confirmedCommand("intent-action", actionType, actorType, reservation, "attempt-1", 20L));

    ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(reservationCaptor.capture());
    assertThat(reservationCaptor.getValue().getStatus()).isEqualTo(expectedStatus);
    assertThat(reservationCaptor.getValue().getEffectiveEscrowStatus())
        .isEqualTo(expectedEscrowStatus);

    ArgumentCaptor<MarketplaceReservationActionState> actionCaptor =
        ArgumentCaptor.forClass(MarketplaceReservationActionState.class);
    then(saveReservationActionStatePort).should().save(actionCaptor.capture());
    assertThat(actionCaptor.getValue().getStatus())
        .isEqualTo(ReservationActionStateStatus.CONFIRMED);

    if (strikeExpected) {
      then(recordTrainerStrikePort)
          .should()
          .recordStrike(
              9L,
              TrainerStrikeEvent.REASON_REJECT,
              RecordTrainerStrikePort.SOURCE_MARKETPLACE_RESERVATION_REJECT,
              "123");
    } else {
      then(recordTrainerStrikePort).shouldHaveNoInteractions();
    }
  }

  @Test
  void confirmedAdminRefundHook_marksTimeoutCancelledWithReason() {
    Reservation reservation = adminRefundPendingReservation();
    MarketplaceReservationActionState actionState =
        activeActionState(
            ReservationEscrowAction.ADMIN_REFUND, ReservationEscrowActorType.ADMIN, 77L);
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-action"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-action"))
        .willReturn(Optional.of(actionState));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));

    service.afterExecutionConfirmed(
        new ReservationEscrowExecutionConfirmedCommand(
            "intent-action",
            "0xtx",
            "MARKETPLACE_ADMIN_REFUND",
            "ADMIN",
            "TRAINER_TIMEOUT",
            reservation.getId(),
            reservation.getOrderKey(),
            null,
            null,
            reservation.sessionEndAt(),
            "attempt-1",
            20L));

    ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(reservationCaptor.capture());
    Reservation updated = reservationCaptor.getValue();
    assertThat(updated.getStatus()).isEqualTo(ReservationStatus.TIMEOUT_CANCELLED);
    assertThat(updated.getEffectiveEscrowStatus()).isEqualTo(ReservationEscrowStatus.REFUNDED);
    assertThat(updated.getResolvedBy()).isEqualTo(ReservationTerminalResolvedBy.ADMIN);
    assertThat(updated.getTerminalReasonCode()).isEqualTo("TRAINER_TIMEOUT");

    ArgumentCaptor<MarketplaceReservationActionState> actionCaptor =
        ArgumentCaptor.forClass(MarketplaceReservationActionState.class);
    then(saveReservationActionStatePort).should().save(actionCaptor.capture());
    assertThat(actionCaptor.getValue().getStatus())
        .isEqualTo(ReservationActionStateStatus.CONFIRMED);

    InOrder lockOrder =
        inOrder(loadReservationActionStatePort, loadReservationPort, loadReservationEscrowPort);
    lockOrder
        .verify(loadReservationActionStatePort)
        .findByExecutionIntentPublicIdWithLock("intent-action");
    lockOrder
        .verify(loadReservationPort)
        .findByCurrentExecutionIntentPublicIdWithLock("intent-action");
    lockOrder.verify(loadReservationEscrowPort).findByReservationIdWithLock(reservation.getId());
  }

  @Test
  void confirmedSchedulerAdminSettleHookMarksResolvedByScheduler() {
    Reservation reservation =
        actionPendingReservation(
            ReservationStatus.ADMIN_SETTLE_PENDING,
            ReservationEscrowStatus.ADMIN_SETTLE_PENDING,
            ReservationEscrowAction.ADMIN_SETTLE,
            null);
    MarketplaceReservationActionState actionState =
        activeActionState(
            ReservationEscrowAction.ADMIN_SETTLE, ReservationEscrowActorType.SYSTEM, null);
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-action"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-action"))
        .willReturn(Optional.of(actionState));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));

    service.afterExecutionConfirmed(
        new ReservationEscrowExecutionConfirmedCommand(
            "intent-action",
            "0xtx",
            "MARKETPLACE_ADMIN_SETTLE",
            "SYSTEM",
            "BUYER_CONFIRMATION_TIMEOUT",
            reservation.getId(),
            reservation.getOrderKey(),
            null,
            null,
            reservation.sessionEndAt(),
            "attempt-1",
            20L));

    ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(reservationCaptor.capture());
    Reservation updated = reservationCaptor.getValue();
    assertThat(updated.getStatus()).isEqualTo(ReservationStatus.AUTO_SETTLED);
    assertThat(updated.getResolvedBy()).isEqualTo(ReservationTerminalResolvedBy.SCHEDULER);
    assertThat(updated.getTerminalReasonCode()).isEqualTo("BUYER_CONFIRMATION_TIMEOUT");
  }

  @Test
  void confirmedPurchaseHook_marksDeadlineSyncRequiredWhenChainReadFails() {
    Reservation reservation = pendingPurchaseReservation();
    RuntimeException rpcFailure = new RuntimeException("rpc unavailable");
    given(loadReservationEscrowOrderPort.getOrder(reservation.getOrderKey())).willThrow(rpcFailure);
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(purchaseActionState()));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));

    service.afterExecutionConfirmed(purchaseConfirmedCommand(reservation));

    ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(reservationCaptor.capture());
    assertThat(reservationCaptor.getValue().getStatus())
        .isEqualTo(ReservationStatus.DEADLINE_SYNC_REQUIRED);

    ArgumentCaptor<MarketplaceReservationEscrow> escrowCaptor =
        ArgumentCaptor.forClass(MarketplaceReservationEscrow.class);
    then(saveReservationEscrowPort).should().save(escrowCaptor.capture());
    assertThat(escrowCaptor.getValue().getEscrowStatus())
        .isEqualTo(ReservationEscrowStatus.DEADLINE_SYNC_REQUIRED);
    assertThat(escrowCaptor.getValue().getLastFailureCode()).isEqualTo("CHAIN_ORDER_READ_FAILED");
  }

  @Test
  void confirmedPurchaseHook_marksDeadlineRecoveryWhenCreatedDeadlineCannotCoverCompletionWindow() {
    Reservation reservation = pendingPurchaseReservation();
    long shortDeadline = Instant.parse("2026-05-21T10:00:00Z").getEpochSecond();
    given(loadReservationEscrowOrderPort.getOrder(reservation.getOrderKey()))
        .willReturn(orderView(ReservationEscrowOrderView.STATE_CREATED, shortDeadline));
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(purchaseActionState()));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));

    service.afterExecutionConfirmed(purchaseConfirmedCommand(reservation));

    ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(reservationCaptor.capture());
    assertThat(reservationCaptor.getValue().getStatus())
        .isEqualTo(ReservationStatus.DEADLINE_RECOVERY_REQUIRED);
    assertThat(reservationCaptor.getValue().getEffectiveEscrowStatus())
        .isEqualTo(ReservationEscrowStatus.DEADLINE_RECOVERY_REQUIRED);
  }

  @Test
  void confirmedPurchaseHook_keepsManualSyncAsAlreadyApplied() {
    Reservation reservation =
        pendingPurchaseReservation()
            .syncChainOutcome(
                ReservationStatus.MANUAL_SYNC_REQUIRED,
                ReservationEscrowStatus.MANUAL_SYNC_REQUIRED,
                "0xtx",
                CONTRACT_DEADLINE_EPOCH_SECONDS,
                LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(CONTRACT_DEADLINE_EPOCH_SECONDS), ZoneOffset.UTC));
    given(loadReservationEscrowOrderPort.getOrder(reservation.getOrderKey()))
        .willReturn(orderView(ReservationEscrowOrderView.STATE_CREATED));
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.empty());
    given(loadReservationPort.findByIdWithLock(reservation.getId()))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(purchaseActionState()));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));

    service.afterExecutionConfirmed(purchaseConfirmedCommand(reservation));

    then(saveReservationPort).shouldHaveNoInteractions();
    ArgumentCaptor<MarketplaceReservationEscrow> escrowCaptor =
        ArgumentCaptor.forClass(MarketplaceReservationEscrow.class);
    then(saveReservationEscrowPort).should().save(escrowCaptor.capture());
    assertThat(escrowCaptor.getValue().getEscrowStatus())
        .isEqualTo(ReservationEscrowStatus.MANUAL_SYNC_REQUIRED);
  }

  @Test
  void terminatedHook_marksMatchingActionStateRetryableForRetryableTerminal() {
    Reservation reservation = pendingCancelReservation();
    MarketplaceReservationActionState actionState = activeActionState();
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-1"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-1"))
        .willReturn(Optional.of(actionState));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));

    service.afterExecutionTerminated(
        new ReservationEscrowExecutionTerminatedCommand(
            "intent-1",
            "MARKETPLACE_CLASS_CANCEL",
            "BUYER",
            reservation.getId(),
            "attempt-1",
            20L,
            "FAILED_ONCHAIN",
            "revert"));

    ArgumentCaptor<MarketplaceReservationActionState> captor =
        ArgumentCaptor.forClass(MarketplaceReservationActionState.class);
    then(saveReservationActionStatePort).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ReservationActionStateStatus.TERMINATED);
    assertThat(captor.getValue().getRetryable()).isFalse();
    assertThat(captor.getValue().getErrorCode()).isEqualTo("FAILED_ONCHAIN");
    ArgumentCaptor<MarketplaceReservationEscrow> escrowCaptor =
        ArgumentCaptor.forClass(MarketplaceReservationEscrow.class);
    then(saveReservationEscrowPort).should().save(escrowCaptor.capture());
    assertThat(escrowCaptor.getValue().getEscrowStatus()).isEqualTo(ReservationEscrowStatus.LOCKED);
    assertThat(escrowCaptor.getValue().getLastFailureCode()).isEqualTo("FAILED_ONCHAIN");
  }

  @Test
  void terminatedPurchaseHook_keepsReservationRecoverableForRetryableTerminal() {
    Reservation reservation = pendingPurchaseReservation();
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(purchaseActionState()));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));

    service.afterExecutionTerminated(
        new ReservationEscrowExecutionTerminatedCommand(
            "intent-purchase",
            "MARKETPLACE_CLASS_PURCHASE",
            "BUYER",
            reservation.getId(),
            "attempt-purchase",
            20L,
            "FAILED_ONCHAIN",
            "temporary revert"));

    then(saveReservationPort).shouldHaveNoInteractions();
    ArgumentCaptor<MarketplaceReservationActionState> actionCaptor =
        ArgumentCaptor.forClass(MarketplaceReservationActionState.class);
    then(saveReservationActionStatePort).should().save(actionCaptor.capture());
    assertThat(actionCaptor.getValue().getStatus())
        .isEqualTo(ReservationActionStateStatus.TERMINATED);
    assertThat(actionCaptor.getValue().getRetryable()).isTrue();

    ArgumentCaptor<MarketplaceReservationEscrow> escrowCaptor =
        ArgumentCaptor.forClass(MarketplaceReservationEscrow.class);
    then(saveReservationEscrowPort).should().save(escrowCaptor.capture());
    assertThat(escrowCaptor.getValue().getEscrowStatus())
        .isEqualTo(ReservationEscrowStatus.PURCHASE_PENDING);
    assertThat(escrowCaptor.getValue().getLastFailureCode()).isEqualTo("FAILED_ONCHAIN");
  }

  @Test
  void terminatedPurchaseHook_marksCreateIdempotencyFailedThroughFallbackPath() {
    Reservation reservation = pendingPurchaseReservation();
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(purchaseActionState()));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));
    given(loadReservationCreateIdempotencyPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(createIdempotency(reservation)));

    service.afterExecutionTerminated(
        new ReservationEscrowExecutionTerminatedCommand(
            "intent-purchase",
            "MARKETPLACE_CLASS_PURCHASE",
            "BUYER",
            reservation.getId(),
            "attempt-purchase",
            20L,
            "REVERTED",
            "fatal revert"));

    ArgumentCaptor<ReservationCreateIdempotency> idempotencyCaptor =
        ArgumentCaptor.forClass(ReservationCreateIdempotency.class);
    then(saveReservationCreateIdempotencyPort).should().save(idempotencyCaptor.capture());
    assertThat(idempotencyCaptor.getValue().getStatus())
        .isEqualTo(ReservationCreateIdempotencyStatus.FAILED);
    assertThat(idempotencyCaptor.getValue().getResponseSnapshotJson()).contains("REVERTED");
  }

  @Test
  void terminatedPurchaseHook_marksCreateIdempotencyFailedThroughPostCommitPath() {
    Reservation reservation = pendingPurchaseReservation();
    AtomicReference<String> callbackName = new AtomicReference<>();
    AtomicBoolean requiresNewCalled = new AtomicBoolean(false);
    service.setPostCommitPort(
        new RunReservationPostCommitPort() {
          @Override
          public void afterCommit(String name, Runnable action) {
            callbackName.set(name);
            action.run();
          }

          @Override
          public void requiresNew(Runnable action) {
            requiresNewCalled.set(true);
            action.run();
          }
        });
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-purchase"))
        .willReturn(Optional.of(purchaseActionState()));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));
    given(loadReservationCreateIdempotencyPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(createIdempotency(reservation)));

    service.afterExecutionTerminated(
        new ReservationEscrowExecutionTerminatedCommand(
            "intent-purchase",
            "MARKETPLACE_CLASS_PURCHASE",
            "BUYER",
            reservation.getId(),
            "attempt-purchase",
            20L,
            "REVERTED",
            "fatal revert"));

    assertThat(callbackName.get()).isEqualTo("MarketplacePurchaseIdempotencyFailure");
    assertThat(requiresNewCalled.get()).isTrue();
    then(saveReservationCreateIdempotencyPort).should().save(any());
  }

  @Test
  void terminatedHook_ignoresUnboundPurchaseIntent() {
    Reservation reservation =
        Reservation.builder()
            .id(123L)
            .userId(7L)
            .trainerId(9L)
            .slotId(11L)
            .reservationDate(LocalDate.of(2026, 5, 20))
            .reservationTime(LocalTime.of(10, 0))
            .durationMinutes(60)
            .status(ReservationStatus.HOLDING)
            .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
            .escrowStatus(ReservationEscrowStatus.PURCHASE_PREPARING)
            .orderId("00000000-0000-0000-0000-000000000123")
            .orderKey("0x" + "0".repeat(61) + "123")
            .pendingAttemptToken("attempt-purchase")
            .bookedPriceAmount(50_000)
            .version(1L)
            .build();
    MarketplaceReservationActionState actionState =
        MarketplaceReservationActionState.builder()
            .id(21L)
            .reservationId(123L)
            .escrowId(10L)
            .actionType(ReservationEscrowAction.PURCHASE)
            .actorType(ReservationEscrowActorType.BUYER)
            .actorUserId(7L)
            .attemptNo(1)
            .attemptToken("attempt-purchase")
            .status(ReservationActionStateStatus.PREPARING)
            .build();
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-unbound"))
        .willReturn(Optional.empty());
    given(loadReservationPort.findByIdWithLock(123L)).willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-unbound"))
        .willReturn(Optional.empty());
    given(loadReservationActionStatePort.findByIdWithLock(21L))
        .willReturn(Optional.of(actionState));

    service.afterExecutionTerminated(
        new ReservationEscrowExecutionTerminatedCommand(
            "intent-unbound",
            "MARKETPLACE_CLASS_PURCHASE",
            "BUYER",
            reservation.getId(),
            "attempt-purchase",
            21L,
            "CANCELED",
            "phase b compensation"));

    then(saveReservationPort).shouldHaveNoInteractions();
    then(saveReservationEscrowPort).shouldHaveNoInteractions();
    ArgumentCaptor<MarketplaceReservationActionState> captor =
        ArgumentCaptor.forClass(MarketplaceReservationActionState.class);
    then(saveReservationActionStatePort).should().save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(ReservationActionStateStatus.TERMINATED);
    assertThat(captor.getValue().getRetryable()).isFalse();
    assertThat(captor.getValue().getErrorCode()).isEqualTo("CANCELED");
  }

  @Test
  void terminatedAdminHook_rollsBackWhenFailedOnchainEvidenceShowsCreatedOrder() {
    Reservation reservation = adminRefundPendingReservation();
    MarketplaceReservationActionState actionState =
        activeActionState(
            ReservationEscrowAction.ADMIN_REFUND, ReservationEscrowActorType.ADMIN, 77L);
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-action"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-action"))
        .willReturn(Optional.of(actionState));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));

    service.afterExecutionTerminated(
        new ReservationEscrowExecutionTerminatedCommand(
            "intent-action",
            "MARKETPLACE_ADMIN_REFUND",
            "ADMIN",
            reservation.getId(),
            "attempt-1",
            20L,
            "FAILED_ONCHAIN",
            "receipt status 0",
            "TRAINER_TIMEOUT",
            evidence("0xdead", true, "FAILED_ONCHAIN", "REVERTED", "CREATED", null)));

    ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(reservationCaptor.capture());
    Reservation updated = reservationCaptor.getValue();
    assertThat(updated.getStatus()).isEqualTo(ReservationStatus.PENDING);
    assertThat(updated.getEffectiveEscrowStatus()).isEqualTo(ReservationEscrowStatus.LOCKED);
    assertThat(updated.getCurrentExecutionIntentPublicId()).isNull();
    assertThat(updated.getPendingAction()).isNull();
    assertThat(updated.getPendingAttemptToken()).isNull();
    assertThat(updated.getPendingActionExpiresAt()).isNull();

    ArgumentCaptor<MarketplaceReservationActionState> actionCaptor =
        ArgumentCaptor.forClass(MarketplaceReservationActionState.class);
    then(saveReservationActionStatePort).should().save(actionCaptor.capture());
    assertThat(actionCaptor.getValue().getStatus())
        .isEqualTo(ReservationActionStateStatus.TERMINATED);
    assertThat(actionCaptor.getValue().getRetryable()).isTrue();
    assertThat(actionCaptor.getValue().getErrorCode()).isEqualTo("FAILED_ONCHAIN");

    ArgumentCaptor<MarketplaceReservationEscrow> escrowCaptor =
        ArgumentCaptor.forClass(MarketplaceReservationEscrow.class);
    then(saveReservationEscrowPort).should().save(escrowCaptor.capture());
    assertThat(escrowCaptor.getValue().getEscrowStatus()).isEqualTo(ReservationEscrowStatus.LOCKED);
    assertThat(escrowCaptor.getValue().getLastTxHash()).isEqualTo("0xdead");
  }

  @Test
  void terminatedAdminHook_marksManualSyncRequiredWhenEvidenceIsUnknown() {
    Reservation reservation = adminRefundPendingReservation();
    MarketplaceReservationActionState actionState =
        activeActionState(
            ReservationEscrowAction.ADMIN_REFUND, ReservationEscrowActorType.ADMIN, 77L);
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-action"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-action"))
        .willReturn(Optional.of(actionState));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));

    service.afterExecutionTerminated(
        new ReservationEscrowExecutionTerminatedCommand(
            "intent-action",
            "MARKETPLACE_ADMIN_REFUND",
            "ADMIN",
            reservation.getId(),
            "attempt-1",
            20L,
            "FAILED_ONCHAIN",
            "receipt unavailable",
            "TRAINER_TIMEOUT",
            evidence(
                "0xdead",
                true,
                "FAILED_ONCHAIN",
                "UNKNOWN",
                "UNKNOWN",
                "CHAIN_ORDER_LOOKUP_FAILED")));

    ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(reservationCaptor.capture());
    Reservation updated = reservationCaptor.getValue();
    assertThat(updated.getStatus()).isEqualTo(ReservationStatus.MANUAL_SYNC_REQUIRED);
    assertThat(updated.getEffectiveEscrowStatus())
        .isEqualTo(ReservationEscrowStatus.MANUAL_SYNC_REQUIRED);
    assertThat(updated.getCurrentExecutionIntentPublicId()).isNull();
    assertThat(updated.getPendingAction()).isNull();
    assertThat(updated.getPendingAttemptToken()).isNull();
    assertThat(updated.getTerminalReasonCode()).isEqualTo("CHAIN_ORDER_LOOKUP_FAILED");

    ArgumentCaptor<MarketplaceReservationActionState> actionCaptor =
        ArgumentCaptor.forClass(MarketplaceReservationActionState.class);
    then(saveReservationActionStatePort).should().save(actionCaptor.capture());
    assertThat(actionCaptor.getValue().getStatus()).isEqualTo(ReservationActionStateStatus.STALE);
    assertThat(actionCaptor.getValue().getRetryable()).isFalse();
    assertThat(actionCaptor.getValue().getErrorCode()).isEqualTo("CHAIN_ORDER_LOOKUP_FAILED");
  }

  @Test
  void terminatedAdminHook_appliesMatchingChainFinalStateWhenAlreadyRefunded() {
    Reservation reservation = adminRefundPendingReservation();
    MarketplaceReservationActionState actionState =
        activeActionState(
            ReservationEscrowAction.ADMIN_REFUND, ReservationEscrowActorType.ADMIN, 77L);
    given(loadReservationPort.findByCurrentExecutionIntentPublicIdWithLock("intent-action"))
        .willReturn(Optional.of(reservation));
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-action"))
        .willReturn(Optional.of(actionState));
    given(loadReservationEscrowPort.findByReservationIdWithLock(reservation.getId()))
        .willReturn(Optional.of(escrowProjection()));

    service.afterExecutionTerminated(
        new ReservationEscrowExecutionTerminatedCommand(
            "intent-action",
            "MARKETPLACE_ADMIN_REFUND",
            "ADMIN",
            reservation.getId(),
            "attempt-1",
            20L,
            "FAILED_ONCHAIN",
            "local receipt unknown",
            "TRAINER_TIMEOUT",
            evidence("0xdead", true, "UNCONFIRMED", "UNKNOWN", "REFUNDED", null)));

    ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
    then(saveReservationPort).should().save(reservationCaptor.capture());
    Reservation updated = reservationCaptor.getValue();
    assertThat(updated.getStatus()).isEqualTo(ReservationStatus.TIMEOUT_CANCELLED);
    assertThat(updated.getEffectiveEscrowStatus()).isEqualTo(ReservationEscrowStatus.REFUNDED);
    assertThat(updated.getResolvedBy()).isEqualTo(ReservationTerminalResolvedBy.ADMIN);
    assertThat(updated.getTerminalReasonCode()).isEqualTo("TRAINER_TIMEOUT");

    ArgumentCaptor<MarketplaceReservationActionState> actionCaptor =
        ArgumentCaptor.forClass(MarketplaceReservationActionState.class);
    then(saveReservationActionStatePort).should().save(actionCaptor.capture());
    assertThat(actionCaptor.getValue().getStatus())
        .isEqualTo(ReservationActionStateStatus.CONFIRMED);
  }

  @Test
  void terminatedAdminHook_skipsActionStateIdFallbackWhenBoundIntentMismatches() {
    MarketplaceReservationActionState mismatchedActionState =
        activeActionState(
                ReservationEscrowAction.ADMIN_REFUND, ReservationEscrowActorType.ADMIN, 77L)
            .toBuilder()
            .executionIntentPublicId("other-intent")
            .build();
    given(loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock("intent-action"))
        .willReturn(Optional.empty());
    given(loadReservationActionStatePort.findByIdWithLock(20L))
        .willReturn(Optional.of(mismatchedActionState));

    service.afterExecutionTerminated(
        new ReservationEscrowExecutionTerminatedCommand(
            "intent-action",
            "MARKETPLACE_ADMIN_REFUND",
            "ADMIN",
            123L,
            "attempt-1",
            20L,
            "FAILED_ONCHAIN",
            "receipt status 0",
            "TRAINER_TIMEOUT",
            evidence("0xdead", true, "FAILED_ONCHAIN", "REVERTED", "CREATED", null)));

    then(loadReservationPort).shouldHaveNoInteractions();
    then(saveReservationPort).shouldHaveNoInteractions();
    then(saveReservationActionStatePort).shouldHaveNoInteractions();
  }

  private Reservation pendingCancelReservation() {
    return Reservation.builder()
        .id(123L)
        .userId(7L)
        .trainerId(9L)
        .slotId(11L)
        .reservationDate(LocalDate.of(2026, 5, 20))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.CANCEL_PENDING)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .escrowStatus(ReservationEscrowStatus.CANCEL_PENDING)
        .orderId("00000000-0000-0000-0000-000000000123")
        .orderKey("0x" + "0".repeat(61) + "123")
        .currentExecutionIntentPublicId("intent-1")
        .pendingAttemptToken("attempt-1")
        .priorStatus(ReservationStatus.PENDING)
        .priorEscrowStatus(ReservationEscrowStatus.LOCKED)
        .bookedPriceAmount(50_000)
        .version(1L)
        .build();
  }

  private Reservation pendingPurchaseReservation() {
    return Reservation.builder()
        .id(123L)
        .userId(7L)
        .trainerId(9L)
        .slotId(11L)
        .reservationDate(LocalDate.of(2026, 5, 20))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(ReservationStatus.HOLDING)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .escrowStatus(ReservationEscrowStatus.PURCHASE_PENDING)
        .orderId("00000000-0000-0000-0000-000000000123")
        .orderKey("0x" + "0".repeat(61) + "123")
        .currentExecutionIntentPublicId("intent-purchase")
        .pendingAttemptToken("attempt-purchase")
        .bookedPriceAmount(50_000)
        .version(1L)
        .build();
  }

  private Reservation adminRefundPendingReservation() {
    return actionPendingReservation(
        ReservationStatus.ADMIN_REFUND_PENDING,
        ReservationEscrowStatus.ADMIN_REFUND_PENDING,
        ReservationEscrowAction.ADMIN_REFUND,
        null);
  }

  private MarketplaceReservationActionState activeActionState() {
    return activeActionState(
        ReservationEscrowAction.BUYER_CANCEL, ReservationEscrowActorType.BUYER, 7L);
  }

  private MarketplaceReservationActionState activeActionState(
      ReservationEscrowAction action, ReservationEscrowActorType actor, Long actorUserId) {
    return MarketplaceReservationActionState.builder()
        .id(20L)
        .reservationId(123L)
        .escrowId(10L)
        .actionType(action)
        .actorType(actor)
        .actorUserId(actorUserId)
        .attemptNo(1)
        .attemptToken("attempt-1")
        .executionIntentPublicId("intent-action")
        .status(ReservationActionStateStatus.INTENT_BOUND)
        .build();
  }

  private static Stream<Arguments> confirmedNonPurchaseActions() {
    return Stream.of(
        Arguments.of(
            "MARKETPLACE_CLASS_CANCEL",
            "BUYER",
            ReservationEscrowAction.BUYER_CANCEL,
            ReservationEscrowActorType.BUYER,
            actionPendingReservation(
                ReservationStatus.CANCEL_PENDING,
                ReservationEscrowStatus.CANCEL_PENDING,
                ReservationEscrowAction.BUYER_CANCEL,
                null),
            ReservationStatus.USER_CANCELLED,
            ReservationEscrowStatus.REFUNDED,
            false),
        Arguments.of(
            "MARKETPLACE_CLASS_CANCEL",
            "TRAINER",
            ReservationEscrowAction.TRAINER_REJECT,
            ReservationEscrowActorType.TRAINER,
            actionPendingReservation(
                ReservationStatus.REJECT_PENDING,
                ReservationEscrowStatus.REJECT_PENDING,
                ReservationEscrowAction.TRAINER_REJECT,
                "trainer rejected"),
            ReservationStatus.REJECTED,
            ReservationEscrowStatus.REFUNDED,
            true),
        Arguments.of(
            "MARKETPLACE_CLASS_CONFIRM",
            "BUYER",
            ReservationEscrowAction.BUYER_CONFIRM,
            ReservationEscrowActorType.BUYER,
            actionPendingReservation(
                ReservationStatus.CONFIRM_PENDING,
                ReservationEscrowStatus.CONFIRM_PENDING,
                ReservationEscrowAction.BUYER_CONFIRM,
                null),
            ReservationStatus.SETTLED,
            ReservationEscrowStatus.SETTLED,
            false),
        Arguments.of(
            "MARKETPLACE_CLASS_EXPIRED_REFUND",
            "BUYER",
            ReservationEscrowAction.DEADLINE_REFUND,
            ReservationEscrowActorType.BUYER,
            actionPendingReservation(
                ReservationStatus.DEADLINE_REFUND_PENDING,
                ReservationEscrowStatus.DEADLINE_REFUND_PENDING,
                ReservationEscrowAction.DEADLINE_REFUND,
                null),
            ReservationStatus.DEADLINE_REFUNDED,
            ReservationEscrowStatus.DEADLINE_REFUNDED,
            false));
  }

  private static Reservation actionPendingReservation(
      ReservationStatus status,
      ReservationEscrowStatus escrowStatus,
      ReservationEscrowAction action,
      String rejectionReason) {
    ReservationStatus priorStatus =
        action == ReservationEscrowAction.BUYER_CONFIRM
            ? ReservationStatus.APPROVED
            : ReservationStatus.PENDING;
    return Reservation.builder()
        .id(123L)
        .userId(7L)
        .trainerId(9L)
        .slotId(11L)
        .reservationDate(LocalDate.of(2026, 5, 20))
        .reservationTime(LocalTime.of(10, 0))
        .durationMinutes(60)
        .status(status)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .escrowStatus(escrowStatus)
        .orderId("00000000-0000-0000-0000-000000000123")
        .orderKey("0x" + "0".repeat(61) + "123")
        .currentExecutionIntentPublicId("intent-action")
        .pendingAttemptToken("attempt-1")
        .pendingAction(action)
        .priorStatus(priorStatus)
        .priorEscrowStatus(ReservationEscrowStatus.LOCKED)
        .rejectionReason(rejectionReason)
        .bookedPriceAmount(50_000)
        .version(1L)
        .build();
  }

  private MarketplaceReservationActionState purchaseActionState() {
    return MarketplaceReservationActionState.builder()
        .id(20L)
        .reservationId(123L)
        .escrowId(10L)
        .actionType(ReservationEscrowAction.PURCHASE)
        .actorType(ReservationEscrowActorType.BUYER)
        .actorUserId(7L)
        .attemptNo(1)
        .attemptToken("attempt-purchase")
        .executionIntentPublicId("intent-purchase")
        .status(ReservationActionStateStatus.INTENT_BOUND)
        .build();
  }

  private ReservationEscrowExecutionTerminationEvidence evidence(
      String txHash,
      boolean hasTxHash,
      String executionTransactionStatus,
      String receiptStatus,
      String chainOrderState,
      String evidenceErrorCode) {
    return new ReservationEscrowExecutionTerminationEvidence(
        txHash,
        hasTxHash,
        "intent-action",
        "intent-action",
        executionTransactionStatus,
        receiptStatus,
        chainOrderState,
        evidenceErrorCode,
        LocalDateTime.of(2026, 5, 16, 0, 0));
  }

  private ReservationCreateIdempotency createIdempotency(Reservation reservation) {
    return ReservationCreateIdempotency.builder()
        .id(30L)
        .buyerId(reservation.getUserId())
        .keyHash("key-hash")
        .payloadHash("payload-hash")
        .status(ReservationCreateIdempotencyStatus.BOUND)
        .reservationId(reservation.getId())
        .escrowId(10L)
        .actionStateId(20L)
        .responseSnapshotJson("{\"status\":\"BOUND\"}")
        .build();
  }

  private ReservationEscrowExecutionConfirmedCommand purchaseConfirmedCommand(
      Reservation reservation) {
    return confirmedCommand(
        "intent-purchase",
        "MARKETPLACE_CLASS_PURCHASE",
        "BUYER",
        reservation,
        "attempt-purchase",
        20L);
  }

  private ReservationEscrowExecutionConfirmedCommand confirmedCommand(
      String intentId,
      String actionType,
      String actorType,
      Reservation reservation,
      String pendingAttemptToken,
      Long actionStateId) {
    return new ReservationEscrowExecutionConfirmedCommand(
        intentId,
        "0xtx",
        actionType,
        actorType,
        reservation.getId(),
        reservation.getOrderKey(),
        CONTRACT_DEADLINE_EPOCH_SECONDS,
        CONTRACT_DEADLINE_EPOCH_SECONDS,
        reservation.sessionEndAt(),
        pendingAttemptToken,
        actionStateId);
  }

  private ReservationEscrowOrderView orderView(int state) {
    return orderView(state, CONTRACT_DEADLINE_EPOCH_SECONDS);
  }

  private ReservationEscrowOrderView orderView(int state, long deadlineEpochSeconds) {
    return new ReservationEscrowOrderView(
        "0x" + "0".repeat(61) + "123",
        "50000",
        "0x3333333333333333333333333333333333333333",
        deadlineEpochSeconds,
        state,
        "0x1111111111111111111111111111111111111111",
        "0x2222222222222222222222222222222222222222");
  }

  private static Stream<Arguments> purchaseConfirmedChainStates() {
    return Stream.of(
        Arguments.of(
            ReservationEscrowOrderView.STATE_CREATED,
            ReservationStatus.PENDING,
            ReservationEscrowStatus.LOCKED),
        Arguments.of(
            ReservationEscrowOrderView.STATE_CONFIRMED,
            ReservationStatus.SETTLED,
            ReservationEscrowStatus.SETTLED),
        Arguments.of(
            ReservationEscrowOrderView.STATE_CANCELLED,
            ReservationStatus.MANUAL_SYNC_REQUIRED,
            ReservationEscrowStatus.MANUAL_SYNC_REQUIRED),
        Arguments.of(
            ReservationEscrowOrderView.STATE_ADMIN_SETTLED,
            ReservationStatus.AUTO_SETTLED,
            ReservationEscrowStatus.SETTLED),
        Arguments.of(
            ReservationEscrowOrderView.STATE_ADMIN_REFUNDED,
            ReservationStatus.TIMEOUT_CANCELLED,
            ReservationEscrowStatus.REFUNDED),
        Arguments.of(
            ReservationEscrowOrderView.STATE_DEADLINE_REFUNDED,
            ReservationStatus.DEADLINE_REFUNDED,
            ReservationEscrowStatus.DEADLINE_REFUNDED),
        Arguments.of(
            9999,
            ReservationStatus.DEADLINE_SYNC_REQUIRED,
            ReservationEscrowStatus.DEADLINE_SYNC_REQUIRED));
  }

  private MarketplaceReservationEscrow escrowProjection() {
    return MarketplaceReservationEscrow.builder()
        .id(10L)
        .reservationId(123L)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .escrowStatus(ReservationEscrowStatus.LOCKED)
        .orderKey("0x" + "0".repeat(61) + "123")
        .build();
  }
}
