package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionConfirmedCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionTerminatedCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionTerminatedCommand.ReservationEscrowExecutionTerminationEvidence;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ApplyReservationEscrowExecutionHookUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RecordTrainerStrikePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationPostCommitPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationEscrow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationTerminalResolvedBy;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.TrainerStrikeEvent;

@RequiredArgsConstructor
@Slf4j
public class ApplyReservationEscrowExecutionHookService
    implements ApplyReservationEscrowExecutionHookUseCase {

  private static final String PURCHASE = "MARKETPLACE_CLASS_PURCHASE";
  private static final String CANCEL = "MARKETPLACE_CLASS_CANCEL";
  private static final String CONFIRM = "MARKETPLACE_CLASS_CONFIRM";
  private static final String EXPIRED_REFUND = "MARKETPLACE_CLASS_EXPIRED_REFUND";
  private static final String ADMIN_REFUND = "MARKETPLACE_ADMIN_REFUND";
  private static final String ADMIN_SETTLE = "MARKETPLACE_ADMIN_SETTLE";
  private static final String TRAINER = "TRAINER";
  private static final String CHAIN_CREATED = "CREATED";
  private static final String CHAIN_CONFIRMED = "CONFIRMED";
  private static final String CHAIN_CANCELLED = "CANCELLED";
  private static final String CHAIN_ADMIN_REFUNDED = "ADMIN_REFUNDED";
  private static final String CHAIN_ADMIN_SETTLED = "ADMIN_SETTLED";
  private static final String CHAIN_DEADLINE_REFUNDED = "DEADLINE_REFUNDED";
  private static final String RECEIPT_REVERTED = "REVERTED";
  private static final String EVIDENCE_UNAVAILABLE = "EVIDENCE_UNAVAILABLE";
  private static final String CHAIN_MISMATCH_REQUIRES_SYNC = "CHAIN_MISMATCH_REQUIRES_SYNC";

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final Clock clock;
  private final RecordTrainerStrikePort recordTrainerStrikePort;
  private final LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;
  private final LoadReservationCreateIdempotencyPort loadReservationCreateIdempotencyPort;
  private final SaveReservationCreateIdempotencyPort saveReservationCreateIdempotencyPort;
  private LoadReservationActionStatePort loadReservationActionStatePort;
  private SaveReservationActionStatePort saveReservationActionStatePort;
  private LoadReservationEscrowPort loadReservationEscrowPort;
  private SaveReservationEscrowPort saveReservationEscrowPort;
  private RunReservationTransactionPort transactionPort;
  private RunReservationPostCommitPort postCommitPort;

  public void setTransactionPort(RunReservationTransactionPort transactionPort) {
    this.transactionPort = java.util.Objects.requireNonNull(transactionPort);
  }

  public void setPostCommitPort(RunReservationPostCommitPort postCommitPort) {
    this.postCommitPort = java.util.Objects.requireNonNull(postCommitPort);
  }

  public void setActionStatePorts(
      LoadReservationActionStatePort loadReservationActionStatePort,
      SaveReservationActionStatePort saveReservationActionStatePort) {
    this.loadReservationActionStatePort = loadReservationActionStatePort;
    this.saveReservationActionStatePort = saveReservationActionStatePort;
  }

  public void setEscrowProjectionPorts(
      LoadReservationEscrowPort loadReservationEscrowPort,
      SaveReservationEscrowPort saveReservationEscrowPort) {
    this.loadReservationEscrowPort = loadReservationEscrowPort;
    this.saveReservationEscrowPort = saveReservationEscrowPort;
  }

  @Override
  public void afterExecutionConfirmed(ReservationEscrowExecutionConfirmedCommand command) {
    if (isAdminAction(command.actionType())) {
      runInNewTransaction(() -> afterAdminExecutionConfirmed(command));
      return;
    }
    ChainOrderLookup chainOrderLookup = loadChainOrderBeforeReservationLock(command);
    runInNewTransaction(() -> afterExecutionConfirmed(command, chainOrderLookup));
  }

  private void afterExecutionConfirmed(
      ReservationEscrowExecutionConfirmedCommand command, ChainOrderLookup chainOrderLookup) {
    Reservation reservation = loadReservationForHook(command.executionIntentPublicId(), command);
    if (isAlreadyApplied(reservation, command.actionType(), command.actorType())) {
      Reservation repaired = repairTxHashIfNeeded(reservation, command.txHash());
      syncEscrowProjection(repaired, command.txHash(), null, null);
      markActionStateConfirmed(command, repaired);
      recordTrainerRejectStrikeAfterCommitIfNeeded(
          reservation, command.actionType(), command.actorType());
      return;
    }
    if (!isCurrentOrRecoverableOrphan(
        command.executionIntentPublicId(), command.pendingAttemptToken(), reservation)) {
      log.warn(
          "Skipping stale marketplace confirmation: intentId={}, reservationId={}",
          command.executionIntentPublicId(),
          reservation.getId());
      return;
    }
    if (chainOrderLookup.failure() != null) {
      Reservation updated =
          reservation.markDeadlineSyncRequired(
              "CHAIN_ORDER_READ_FAILED", chainOrderLookup.failure().getMessage());
      saveReservationPort.save(updated);
      syncEscrowProjection(
          updated,
          command.txHash(),
          "CHAIN_ORDER_READ_FAILED",
          chainOrderLookup.failure().getMessage());
      markActionStateConfirmed(command, updated);
      return;
    }
    Reservation updated =
        switch (command.actionType()) {
          case PURCHASE -> applyPurchaseConfirmed(reservation, command, chainOrderLookup.order());
          case CANCEL ->
              TRAINER.equals(command.actorType())
                  ? reservation.reject(command.txHash(), reservation.getRejectionReason())
                  : reservation.cancelByUser(command.txHash());
          case CONFIRM -> reservation.complete(command.txHash());
          case EXPIRED_REFUND -> reservation.markDeadlineRefunded(command.txHash());
          case ADMIN_REFUND ->
              reservation.markAdminRefunded(
                  command.txHash(), resolvedBy(command.actorType()), command.terminalReasonCode());
          case ADMIN_SETTLE ->
              reservation.markAdminSettled(
                  command.txHash(), resolvedBy(command.actorType()), command.terminalReasonCode());
          default -> throw new IllegalStateException("unsupported marketplace action");
        };
    saveReservationPort.save(updated);
    syncEscrowProjection(updated, command.txHash(), null, null);
    markActionStateConfirmed(command, updated);
    recordTrainerRejectStrikeAfterCommitIfNeeded(
        updated, command.actionType(), command.actorType());
  }

  @Override
  public void afterExecutionTerminated(ReservationEscrowExecutionTerminatedCommand command) {
    if (isAdminAction(command.actionType())) {
      runInNewTransaction(() -> afterAdminExecutionTerminated(command));
      return;
    }
    runInNewTransaction(() -> afterExecutionTerminatedInTransaction(command));
  }

  private void afterExecutionTerminatedInTransaction(
      ReservationEscrowExecutionTerminatedCommand command) {
    Reservation reservation = loadReservationForHook(command.executionIntentPublicId(), command);
    if (!isCurrentOrRecoverableOrphan(
        command.executionIntentPublicId(), command.pendingAttemptToken(), reservation)) {
      return;
    }
    if (isUnboundPurchaseTermination(command, reservation)) {
      log.info(
          "Skipping unbound marketplace purchase termination: intentId={}, reservationId={}",
          command.executionIntentPublicId(),
          reservation.getId());
      markActionStateTerminated(command, reservation, false);
      return;
    }
    if (isRetryablePurchaseTermination(command)) {
      syncEscrowProjection(reservation, null, command.terminalStatus(), command.failureReason());
      markActionStateTerminated(command, reservation, true);
      return;
    }
    Reservation updated =
        PURCHASE.equals(command.actionType())
            ? reservation.markPaymentFailed(command.terminalStatus(), command.failureReason())
            : reservation.rollbackToPriorState();
    saveReservationPort.save(updated);
    syncEscrowProjection(updated, null, command.terminalStatus(), command.failureReason());
    markActionStateTerminated(command, updated, false);
    markPurchaseCreateIdempotencyFailedAfterCommitIfNeeded(command, reservation);
  }

  private void afterAdminExecutionConfirmed(ReservationEscrowExecutionConfirmedCommand command) {
    LockedAdminHookState locked =
        lockAdminHookState(
            command.executionIntentPublicId(),
            command.actionStateId(),
            command.pendingAttemptToken(),
            command.reservationId(),
            true);
    if (locked == null) {
      return;
    }
    Reservation reservation = locked.reservation();
    if (isAlreadyApplied(reservation, command.actionType(), command.actorType())) {
      Reservation repaired = repairTxHashIfNeeded(reservation, command.txHash());
      syncEscrowProjection(repaired, command.txHash(), null, null);
      markActionStateConfirmed(command, repaired, locked.actionState());
      return;
    }
    if (!isCurrentOrRecoverableOrphan(
        command.executionIntentPublicId(), command.pendingAttemptToken(), reservation)) {
      log.warn(
          "Skipping stale marketplace admin confirmation: intentId={}, reservationId={}",
          command.executionIntentPublicId(),
          reservation.getId());
      return;
    }
    Reservation updated =
        ADMIN_REFUND.equals(command.actionType())
            ? reservation.markAdminRefunded(
                command.txHash(), resolvedBy(command.actorType()), command.terminalReasonCode())
            : reservation.markAdminSettled(
                command.txHash(), resolvedBy(command.actorType()), command.terminalReasonCode());
    saveReservationPort.save(updated);
    syncEscrowProjection(updated, command.txHash(), null, null);
    markActionStateConfirmed(command, updated, locked.actionState());
  }

  private void afterAdminExecutionTerminated(ReservationEscrowExecutionTerminatedCommand command) {
    LockedAdminHookState locked =
        lockAdminHookState(
            command.executionIntentPublicId(),
            command.actionStateId(),
            command.pendingAttemptToken(),
            command.reservationId(),
            true);
    if (locked == null) {
      return;
    }
    Reservation reservation = locked.reservation();
    if (!isCurrentOrRecoverableOrphan(
        command.executionIntentPublicId(), command.pendingAttemptToken(), reservation)) {
      return;
    }
    applyAdminTermination(command, reservation, locked.actionState());
  }

  private void applyAdminTermination(
      ReservationEscrowExecutionTerminatedCommand command,
      Reservation reservation,
      MarketplaceReservationActionState actionState) {
    ReservationEscrowExecutionTerminationEvidence evidence = evidence(command);
    if (isConfirmedByChain(command.actionType(), evidence.chainOrderState())) {
      Reservation updated =
          ADMIN_REFUND.equals(command.actionType())
              ? reservation.markAdminRefunded(
                  evidence.txHash(), resolvedBy(command.actorType()), command.reasonCode())
              : reservation.markAdminSettled(
                  evidence.txHash(), resolvedBy(command.actorType()), command.reasonCode());
      saveReservationPort.save(updated);
      syncEscrowProjection(updated, evidence.txHash(), null, null);
      markAdminActionStateConfirmed(command, updated, actionState);
      return;
    }
    ChainTerminalOutcome chainTerminalOutcome = chainTerminalOutcome(evidence);
    if (chainTerminalOutcome != null) {
      Reservation updated =
          reservation.syncChainOutcome(
              chainTerminalOutcome.status(),
              chainTerminalOutcome.escrowStatus(),
              evidence.txHash(),
              reservation.getContractDeadlineEpochSeconds(),
              reservation.getContractDeadlineAt(),
              ReservationTerminalResolvedBy.CHAIN_SYNC,
              CHAIN_MISMATCH_REQUIRES_SYNC);
      saveReservationPort.save(updated);
      syncEscrowProjection(
          updated, evidence.txHash(), CHAIN_MISMATCH_REQUIRES_SYNC, command.failureReason());
      markActionStateStale(
          command, updated, CHAIN_MISMATCH_REQUIRES_SYNC, command.failureReason(), actionState);
      return;
    }
    if (isRollbackSafeAdminTermination(command, evidence)) {
      Reservation updated = reservation.rollbackToPriorState();
      saveReservationPort.save(updated);
      syncEscrowProjection(
          updated, evidence.txHash(), command.terminalStatus(), command.failureReason());
      markActionStateTerminated(
          command, updated, isRetryableTerminal(command.terminalStatus()), actionState);
      return;
    }
    String errorCode = syncRequiredErrorCode(evidence);
    Reservation updated =
        reservation.syncChainOutcome(
            ReservationStatus.MANUAL_SYNC_REQUIRED,
            ReservationEscrowStatus.MANUAL_SYNC_REQUIRED,
            evidence.txHash(),
            reservation.getContractDeadlineEpochSeconds(),
            reservation.getContractDeadlineAt(),
            ReservationTerminalResolvedBy.CHAIN_SYNC,
            errorCode);
    saveReservationPort.save(updated);
    syncEscrowProjection(updated, evidence.txHash(), errorCode, command.failureReason());
    markActionStateStale(command, updated, errorCode, command.failureReason(), actionState);
  }

  private boolean isAdminAction(String actionType) {
    return ADMIN_REFUND.equals(actionType) || ADMIN_SETTLE.equals(actionType);
  }

  private boolean isConfirmedByChain(String actionType, String chainOrderState) {
    return (ADMIN_REFUND.equals(actionType) && CHAIN_ADMIN_REFUNDED.equals(chainOrderState))
        || (ADMIN_SETTLE.equals(actionType) && CHAIN_ADMIN_SETTLED.equals(chainOrderState));
  }

  private ChainTerminalOutcome chainTerminalOutcome(
      ReservationEscrowExecutionTerminationEvidence evidence) {
    if (evidence.chainOrderState() == null) {
      return null;
    }
    return switch (evidence.chainOrderState()) {
      case CHAIN_CONFIRMED ->
          new ChainTerminalOutcome(ReservationStatus.SETTLED, ReservationEscrowStatus.SETTLED);
      case CHAIN_CANCELLED ->
          new ChainTerminalOutcome(
              ReservationStatus.USER_CANCELLED, ReservationEscrowStatus.REFUNDED);
      case CHAIN_ADMIN_SETTLED ->
          new ChainTerminalOutcome(ReservationStatus.AUTO_SETTLED, ReservationEscrowStatus.SETTLED);
      case CHAIN_ADMIN_REFUNDED ->
          new ChainTerminalOutcome(
              ReservationStatus.TIMEOUT_CANCELLED, ReservationEscrowStatus.REFUNDED);
      case CHAIN_DEADLINE_REFUNDED ->
          new ChainTerminalOutcome(
              ReservationStatus.DEADLINE_REFUNDED, ReservationEscrowStatus.DEADLINE_REFUNDED);
      default -> null;
    };
  }

  private boolean isRollbackSafeAdminTermination(
      ReservationEscrowExecutionTerminatedCommand command,
      ReservationEscrowExecutionTerminationEvidence evidence) {
    return switch (command.terminalStatus()) {
      case "EXPIRED", "CANCELED" ->
          !evidence.hasTxHash() && CHAIN_CREATED.equals(evidence.chainOrderState());
      case "NONCE_STALE" ->
          !evidence.hasTxHash() && CHAIN_CREATED.equals(evidence.chainOrderState());
      case "FAILED_ONCHAIN" ->
          RECEIPT_REVERTED.equals(evidence.receiptStatus())
              && CHAIN_CREATED.equals(evidence.chainOrderState());
      default -> false;
    };
  }

  private ReservationEscrowExecutionTerminationEvidence evidence(
      ReservationEscrowExecutionTerminatedCommand command) {
    return command.evidence() == null
        ? ReservationEscrowExecutionTerminationEvidence.unknown(command.executionIntentPublicId())
        : command.evidence();
  }

  private String syncRequiredErrorCode(ReservationEscrowExecutionTerminationEvidence evidence) {
    if (evidence.evidenceErrorCode() != null && !evidence.evidenceErrorCode().isBlank()) {
      return evidence.evidenceErrorCode();
    }
    return evidence.chainOrderState() == null ? EVIDENCE_UNAVAILABLE : CHAIN_MISMATCH_REQUIRES_SYNC;
  }

  private boolean isRetryablePurchaseTermination(
      ReservationEscrowExecutionTerminatedCommand command) {
    return PURCHASE.equals(command.actionType()) && isRetryableTerminal(command.terminalStatus());
  }

  private boolean isUnboundPurchaseTermination(
      ReservationEscrowExecutionTerminatedCommand command, Reservation reservation) {
    return PURCHASE.equals(command.actionType())
        && reservation.getCurrentExecutionIntentPublicId() == null
        && command.pendingAttemptToken() != null
        && command.pendingAttemptToken().equals(reservation.getPendingAttemptToken());
  }

  private Reservation loadReservationForHook(
      String executionIntentPublicId, ReservationEscrowExecutionConfirmedCommand command) {
    return loadReservationPort
        .findByCurrentExecutionIntentPublicIdWithLock(executionIntentPublicId)
        .or(() -> loadReservationPort.findByIdWithLock(command.reservationId()))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "marketplace reservation not found for intentId=" + executionIntentPublicId));
  }

  private Reservation loadReservationForHook(
      String executionIntentPublicId, ReservationEscrowExecutionTerminatedCommand command) {
    return loadReservationPort
        .findByCurrentExecutionIntentPublicIdWithLock(executionIntentPublicId)
        .or(() -> loadReservationPort.findByIdWithLock(command.reservationId()))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "marketplace reservation not found for intentId=" + executionIntentPublicId));
  }

  private LockedAdminHookState lockAdminHookState(
      String executionIntentPublicId,
      Long actionStateId,
      String pendingAttemptToken,
      Long fallbackReservationId,
      boolean requireBoundIntent) {
    Reservation reservation =
        loadReservationPort
            .findByCurrentExecutionIntentPublicIdWithLock(executionIntentPublicId)
            .or(() -> loadReservationPort.findByIdWithLock(fallbackReservationId))
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "marketplace reservation not found for intentId="
                            + executionIntentPublicId));
    lockEscrowProjectionForHook(reservation.getId());
    java.util.Optional<MarketplaceReservationActionState> actionState =
        lockActionStateForHook(
            executionIntentPublicId, actionStateId, pendingAttemptToken, requireBoundIntent);
    if (actionState.isEmpty()) {
      if (loadReservationActionStatePort != null && saveReservationActionStatePort != null) {
        log.warn(
            "Skipping marketplace admin hook because action state was not found: intentId={}, actionStateId={}",
            executionIntentPublicId,
            actionStateId);
        return null;
      }
      return new LockedAdminHookState(null, reservation);
    }
    MarketplaceReservationActionState lockedActionState = actionState.get();
    if (!actionStateMatches(
        lockedActionState,
        reservation,
        pendingAttemptToken,
        executionIntentPublicId,
        requireBoundIntent)) {
      log.warn(
          "Skipping marketplace admin hook because action state mismatched: intentId={}, actionStateId={}, reservationId={}",
          executionIntentPublicId,
          lockedActionState.getId(),
          reservation.getId());
      return null;
    }
    return new LockedAdminHookState(lockedActionState, reservation);
  }

  private void lockEscrowProjectionForHook(Long reservationId) {
    if (loadReservationEscrowPort != null) {
      loadReservationEscrowPort.findByReservationIdWithLock(reservationId);
    }
  }

  private java.util.Optional<MarketplaceReservationActionState> lockActionStateForHook(
      String executionIntentPublicId,
      Long actionStateId,
      String pendingAttemptToken,
      boolean requireBoundIntent) {
    if (loadReservationActionStatePort == null || saveReservationActionStatePort == null) {
      return java.util.Optional.empty();
    }
    java.util.Optional<MarketplaceReservationActionState> byIntent =
        loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock(
            executionIntentPublicId);
    if (byIntent.isPresent()) {
      return byIntent.filter(
          actionState ->
              actionStateTokenMatches(actionState, pendingAttemptToken)
                  && actionStateBoundIntentMatches(
                      actionState, executionIntentPublicId, requireBoundIntent));
    }
    if (actionStateId == null) {
      return java.util.Optional.empty();
    }
    return loadReservationActionStatePort
        .findByIdWithLock(actionStateId)
        .filter(
            actionState ->
                actionStateTokenMatches(actionState, pendingAttemptToken)
                    && actionStateBoundIntentMatches(
                        actionState, executionIntentPublicId, requireBoundIntent));
  }

  private boolean isCurrentOrRecoverableOrphan(
      String executionIntentPublicId, String pendingAttemptToken, Reservation reservation) {
    String currentIntentId = reservation.getCurrentExecutionIntentPublicId();
    if (executionIntentPublicId.equals(currentIntentId)) {
      return true;
    }
    if (currentIntentId != null && !currentIntentId.isBlank()) {
      return false;
    }
    return pendingAttemptToken != null
        && pendingAttemptToken.equals(reservation.getPendingAttemptToken());
  }

  private boolean isAlreadyApplied(Reservation reservation, String actionType, String actorType) {
    return switch (actionType) {
      case PURCHASE ->
          reservation.getStatus() == ReservationStatus.PENDING
              || reservation.getStatus() == ReservationStatus.DEADLINE_RECOVERY_REQUIRED
              || reservation.getStatus() == ReservationStatus.DEADLINE_SYNC_REQUIRED
              || reservation.getStatus() == ReservationStatus.SETTLED
              || reservation.getStatus() == ReservationStatus.MANUAL_SYNC_REQUIRED
              || reservation.getStatus() == ReservationStatus.AUTO_SETTLED
              || reservation.getStatus() == ReservationStatus.TIMEOUT_CANCELLED
              || reservation.getStatus() == ReservationStatus.DEADLINE_REFUNDED;
      case CANCEL ->
          TRAINER.equals(actorType)
              ? reservation.getStatus() == ReservationStatus.REJECTED
              : reservation.getStatus() == ReservationStatus.USER_CANCELLED;
      case CONFIRM -> reservation.getStatus() == ReservationStatus.SETTLED;
      case EXPIRED_REFUND -> reservation.getStatus() == ReservationStatus.DEADLINE_REFUNDED;
      case ADMIN_REFUND -> reservation.getStatus() == ReservationStatus.TIMEOUT_CANCELLED;
      case ADMIN_SETTLE -> reservation.getStatus() == ReservationStatus.AUTO_SETTLED;
      default -> false;
    };
  }

  private void recordTrainerRejectStrikeAfterCommitIfNeeded(
      Reservation reservation, String actionType, String actorType) {
    if (!CANCEL.equals(actionType)
        || !TRAINER.equals(actorType)
        || recordTrainerStrikePort == null) {
      return;
    }
    if (postCommitPort == null) {
      recordTrainerRejectStrike(reservation);
      return;
    }
    postCommitPort.afterCommit(
        "MarketplaceTrainerRejectStrike",
        () -> postCommitPort.requiresNew(() -> recordTrainerRejectStrike(reservation)));
  }

  private void recordTrainerRejectStrike(Reservation reservation) {
    try {
      recordTrainerStrikePort.recordStrike(
          reservation.getTrainerId(),
          TrainerStrikeEvent.REASON_REJECT,
          RecordTrainerStrikePort.SOURCE_MARKETPLACE_RESERVATION_REJECT,
          String.valueOf(reservation.getId()));
    } catch (RuntimeException e) {
      log.warn(
          "Failed to record trainer reject strike after confirmed hook: id={}, trainerId={}",
          reservation.getId(),
          reservation.getTrainerId(),
          e);
    }
  }

  private Reservation applyPurchaseConfirmed(
      Reservation reservation,
      ReservationEscrowExecutionConfirmedCommand command,
      ReservationEscrowOrderView chainOrder) {
    if (chainOrder != null) {
      return applyPurchaseChainState(reservation, command, chainOrder);
    }
    Long deadlineEpochSeconds =
        command.contractDeadlineEpochSeconds() == null
            ? command.expectedContractDeadlineEpochSeconds()
            : command.contractDeadlineEpochSeconds();
    LocalDateTime deadlineAt = deadlineAt(deadlineEpochSeconds);
    if (deadlineAt != null && command.sessionEndAt().plusHours(24).isAfter(deadlineAt)) {
      return reservation.markPurchaseDeadlineRecoveryRequired(
          deadlineEpochSeconds, deadlineAt, command.txHash());
    }
    return reservation.markPurchaseConfirmedLocked(
        deadlineEpochSeconds, deadlineAt, command.txHash());
  }

  private Reservation applyPurchaseChainState(
      Reservation reservation,
      ReservationEscrowExecutionConfirmedCommand command,
      ReservationEscrowOrderView order) {
    if (order.isAbsent()) {
      return reservation.markDeadlineSyncRequired(
          "ORDER_ABSENT_AFTER_CONFIRMED",
          "confirmed marketplace purchase intent has no matching on-chain order");
    }
    LocalDateTime deadlineAt = deadlineAt(order.deadlineEpochSeconds());
    return switch (order.state()) {
      case ReservationEscrowOrderView.STATE_CREATED -> {
        if (deadlineAt != null && command.sessionEndAt().plusHours(24).isAfter(deadlineAt)) {
          yield reservation.markPurchaseDeadlineRecoveryRequired(
              order.deadlineEpochSeconds(), deadlineAt, command.txHash());
        }
        yield reservation.markPurchaseConfirmedLocked(
            order.deadlineEpochSeconds(), deadlineAt, command.txHash());
      }
      case ReservationEscrowOrderView.STATE_CONFIRMED ->
          reservation.syncChainOutcome(
              ReservationStatus.SETTLED,
              ReservationEscrowStatus.SETTLED,
              command.txHash(),
              order.deadlineEpochSeconds(),
              deadlineAt);
      case ReservationEscrowOrderView.STATE_CANCELLED ->
          reservation.syncChainOutcome(
              ReservationStatus.MANUAL_SYNC_REQUIRED,
              ReservationEscrowStatus.MANUAL_SYNC_REQUIRED,
              command.txHash(),
              order.deadlineEpochSeconds(),
              deadlineAt);
      case ReservationEscrowOrderView.STATE_ADMIN_SETTLED ->
          reservation.syncChainOutcome(
              ReservationStatus.AUTO_SETTLED,
              ReservationEscrowStatus.SETTLED,
              command.txHash(),
              order.deadlineEpochSeconds(),
              deadlineAt);
      case ReservationEscrowOrderView.STATE_ADMIN_REFUNDED ->
          reservation.syncChainOutcome(
              ReservationStatus.TIMEOUT_CANCELLED,
              ReservationEscrowStatus.REFUNDED,
              command.txHash(),
              order.deadlineEpochSeconds(),
              deadlineAt);
      case ReservationEscrowOrderView.STATE_DEADLINE_REFUNDED ->
          reservation.syncChainOutcome(
              ReservationStatus.DEADLINE_REFUNDED,
              ReservationEscrowStatus.DEADLINE_REFUNDED,
              command.txHash(),
              order.deadlineEpochSeconds(),
              deadlineAt);
      default ->
          reservation.markDeadlineSyncRequired(
              "UNKNOWN_ORDER_STATE", "Unsupported marketplace order state: " + order.state());
    };
  }

  private ChainOrderLookup loadChainOrderBeforeReservationLock(
      ReservationEscrowExecutionConfirmedCommand command) {
    if (!PURCHASE.equals(command.actionType()) || loadReservationEscrowOrderPort == null) {
      return ChainOrderLookup.notLoaded();
    }
    try {
      return runWithoutTransaction(
          () ->
              ChainOrderLookup.loaded(loadReservationEscrowOrderPort.getOrder(command.orderKey())));
    } catch (RuntimeException e) {
      log.warn(
          "Failed to load marketplace chain order before reservation hook lock: reservationId={}, orderKey={}",
          command.reservationId(),
          command.orderKey(),
          e);
      return ChainOrderLookup.failed(e);
    }
  }

  private <T> T runWithoutTransaction(java.util.function.Supplier<T> supplier) {
    return transactionPort == null ? supplier.get() : transactionPort.notSupported(supplier);
  }

  private void runInNewTransaction(Runnable action) {
    if (transactionPort == null) {
      action.run();
      return;
    }
    transactionPort.requiresNew(action);
  }

  private void markPurchaseCreateIdempotencyFailedAfterCommitIfNeeded(
      ReservationEscrowExecutionTerminatedCommand command, Reservation reservation) {
    if (!PURCHASE.equals(command.actionType())
        || loadReservationCreateIdempotencyPort == null
        || saveReservationCreateIdempotencyPort == null) {
      return;
    }
    if (postCommitPort == null) {
      markPurchaseCreateIdempotencyFailed(command, reservation);
      return;
    }
    postCommitPort.afterCommit(
        "MarketplacePurchaseIdempotencyFailure",
        () ->
            postCommitPort.requiresNew(
                () -> markPurchaseCreateIdempotencyFailed(command, reservation)));
  }

  private void markPurchaseCreateIdempotencyFailed(
      ReservationEscrowExecutionTerminatedCommand command, Reservation reservation) {
    loadReservationCreateIdempotencyPort
        .findByReservationIdWithLock(reservation.getId())
        .ifPresent(
            idempotency ->
                saveReservationCreateIdempotencyPort.save(
                    idempotency.markFailed(
                        "{\"status\":\"FAILED\",\"terminalStatus\":\""
                            + command.terminalStatus()
                            + "\"}")));
  }

  private void syncEscrowProjection(
      Reservation reservation, String txHash, String failureCode, String failureMessage) {
    if (loadReservationEscrowPort == null || saveReservationEscrowPort == null) {
      return;
    }
    loadReservationEscrowPort
        .findByReservationIdWithLock(reservation.getId())
        .map(
            escrow ->
                updateEscrowProjection(escrow, reservation, txHash, failureCode, failureMessage))
        .ifPresent(saveReservationEscrowPort::save);
  }

  private MarketplaceReservationEscrow updateEscrowProjection(
      MarketplaceReservationEscrow escrow,
      Reservation reservation,
      String txHash,
      String failureCode,
      String failureMessage) {
    return escrow.toBuilder()
        .escrowStatus(reservation.getEffectiveEscrowStatus())
        .contractDeadlineEpochSeconds(reservation.getContractDeadlineEpochSeconds())
        .contractDeadlineAt(reservation.getContractDeadlineAt())
        .lastTxHash(txHash == null ? escrow.getLastTxHash() : txHash)
        .lastFailureCode(failureCode)
        .lastFailureMessage(failureMessage)
        .build();
  }

  private void markActionStateConfirmed(
      ReservationEscrowExecutionConfirmedCommand command, Reservation reservation) {
    markActionStateConfirmed(command, reservation, null);
  }

  private void markActionStateConfirmed(
      ReservationEscrowExecutionConfirmedCommand command,
      Reservation reservation,
      MarketplaceReservationActionState lockedActionState) {
    if (lockedActionState != null) {
      saveReservationActionStatePort.save(
          lockedActionState.toBuilder()
              .executionIntentPublicId(command.executionIntentPublicId())
              .status(ReservationActionStateStatus.CONFIRMED)
              .retryable(false)
              .errorCode(null)
              .errorReason(null)
              .build());
      return;
    }
    findActionStateForHook(
            command.executionIntentPublicId(),
            command.actionStateId(),
            command.pendingAttemptToken(),
            reservation)
        .ifPresent(
            actionState ->
                saveReservationActionStatePort.save(
                    actionState.toBuilder()
                        .executionIntentPublicId(command.executionIntentPublicId())
                        .status(ReservationActionStateStatus.CONFIRMED)
                        .retryable(false)
                        .errorCode(null)
                        .errorReason(null)
                        .build()));
  }

  private void markActionStateTerminated(
      ReservationEscrowExecutionTerminatedCommand command,
      Reservation reservation,
      boolean retryable) {
    markActionStateTerminated(command, reservation, retryable, null);
  }

  private void markActionStateTerminated(
      ReservationEscrowExecutionTerminatedCommand command,
      Reservation reservation,
      boolean retryable,
      MarketplaceReservationActionState lockedActionState) {
    if (lockedActionState != null) {
      saveReservationActionStatePort.save(
          lockedActionState.toBuilder()
              .executionIntentPublicId(command.executionIntentPublicId())
              .status(ReservationActionStateStatus.TERMINATED)
              .retryable(retryable)
              .errorCode(command.terminalStatus())
              .errorReason(command.failureReason())
              .build());
      return;
    }
    findActionStateForHook(
            command.executionIntentPublicId(),
            command.actionStateId(),
            command.pendingAttemptToken(),
            reservation)
        .ifPresent(
            actionState ->
                saveReservationActionStatePort.save(
                    actionState.toBuilder()
                        .executionIntentPublicId(command.executionIntentPublicId())
                        .status(ReservationActionStateStatus.TERMINATED)
                        .retryable(retryable)
                        .errorCode(command.terminalStatus())
                        .errorReason(command.failureReason())
                        .build()));
  }

  private void markAdminActionStateConfirmed(
      ReservationEscrowExecutionTerminatedCommand command, Reservation reservation) {
    markAdminActionStateConfirmed(command, reservation, null);
  }

  private void markAdminActionStateConfirmed(
      ReservationEscrowExecutionTerminatedCommand command,
      Reservation reservation,
      MarketplaceReservationActionState lockedActionState) {
    if (lockedActionState != null) {
      saveReservationActionStatePort.save(
          lockedActionState.toBuilder()
              .executionIntentPublicId(command.executionIntentPublicId())
              .status(ReservationActionStateStatus.CONFIRMED)
              .retryable(false)
              .errorCode(null)
              .errorReason(null)
              .build());
      return;
    }
    findActionStateForHook(
            command.executionIntentPublicId(),
            command.actionStateId(),
            command.pendingAttemptToken(),
            reservation)
        .ifPresent(
            actionState ->
                saveReservationActionStatePort.save(
                    actionState.toBuilder()
                        .executionIntentPublicId(command.executionIntentPublicId())
                        .status(ReservationActionStateStatus.CONFIRMED)
                        .retryable(false)
                        .errorCode(null)
                        .errorReason(null)
                        .build()));
  }

  private void markActionStateStale(
      ReservationEscrowExecutionTerminatedCommand command,
      Reservation reservation,
      String errorCode,
      String errorReason) {
    markActionStateStale(command, reservation, errorCode, errorReason, null);
  }

  private void markActionStateStale(
      ReservationEscrowExecutionTerminatedCommand command,
      Reservation reservation,
      String errorCode,
      String errorReason,
      MarketplaceReservationActionState lockedActionState) {
    if (lockedActionState != null) {
      saveReservationActionStatePort.save(
          lockedActionState.toBuilder()
              .executionIntentPublicId(command.executionIntentPublicId())
              .status(ReservationActionStateStatus.STALE)
              .retryable(false)
              .errorCode(errorCode)
              .errorReason(errorReason)
              .build());
      return;
    }
    findActionStateForHook(
            command.executionIntentPublicId(),
            command.actionStateId(),
            command.pendingAttemptToken(),
            reservation)
        .ifPresent(
            actionState ->
                saveReservationActionStatePort.save(
                    actionState.toBuilder()
                        .executionIntentPublicId(command.executionIntentPublicId())
                        .status(ReservationActionStateStatus.STALE)
                        .retryable(false)
                        .errorCode(errorCode)
                        .errorReason(errorReason)
                        .build()));
  }

  private java.util.Optional<MarketplaceReservationActionState> findActionStateForHook(
      String executionIntentPublicId,
      Long actionStateId,
      String pendingAttemptToken,
      Reservation reservation) {
    if (loadReservationActionStatePort == null || saveReservationActionStatePort == null) {
      return java.util.Optional.empty();
    }
    java.util.Optional<MarketplaceReservationActionState> byIntent =
        loadReservationActionStatePort.findByExecutionIntentPublicIdWithLock(
            executionIntentPublicId);
    if (byIntent.isPresent()) {
      return byIntent.filter(
          actionState -> actionStateMatches(actionState, reservation, pendingAttemptToken));
    }
    if (actionStateId == null) {
      return java.util.Optional.empty();
    }
    return loadReservationActionStatePort
        .findByIdWithLock(actionStateId)
        .filter(actionState -> actionStateMatches(actionState, reservation, pendingAttemptToken));
  }

  private boolean actionStateMatches(
      MarketplaceReservationActionState actionState,
      Reservation reservation,
      String pendingAttemptToken) {
    return reservation.getId().equals(actionState.getReservationId())
        && actionStateTokenMatches(actionState, pendingAttemptToken);
  }

  private boolean actionStateMatches(
      MarketplaceReservationActionState actionState,
      Reservation reservation,
      String pendingAttemptToken,
      String executionIntentPublicId,
      boolean requireBoundIntent) {
    return actionStateMatches(actionState, reservation, pendingAttemptToken)
        && actionStateBoundIntentMatches(actionState, executionIntentPublicId, requireBoundIntent);
  }

  private boolean actionStateBoundIntentMatches(
      MarketplaceReservationActionState actionState,
      String executionIntentPublicId,
      boolean requireBoundIntent) {
    if (!requireBoundIntent) {
      return true;
    }
    return actionState.getStatus() == ReservationActionStateStatus.INTENT_BOUND
        && executionIntentPublicId != null
        && executionIntentPublicId.equals(actionState.getExecutionIntentPublicId());
  }

  private boolean actionStateTokenMatches(
      MarketplaceReservationActionState actionState, String pendingAttemptToken) {
    return pendingAttemptToken == null || pendingAttemptToken.equals(actionState.getAttemptToken());
  }

  private boolean isRetryableTerminal(String terminalStatus) {
    return ReservationExecutionTerminalStatusPolicy.isRetryableTerminal(terminalStatus);
  }

  private static ReservationTerminalResolvedBy resolvedBy(String actorType) {
    return "SYSTEM".equals(actorType)
        ? ReservationTerminalResolvedBy.SCHEDULER
        : ReservationTerminalResolvedBy.ADMIN;
  }

  private Reservation repairTxHashIfNeeded(Reservation reservation, String txHash) {
    if (txHash == null || txHash.equals(reservation.getTxHash())) {
      return reservation;
    }
    Reservation repaired = reservation.updateTxHash(txHash);
    saveReservationPort.save(repaired);
    return repaired;
  }

  private LocalDateTime deadlineAt(Long epochSeconds) {
    if (epochSeconds == null) {
      return null;
    }
    return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), clock.getZone());
  }

  private record LockedAdminHookState(
      MarketplaceReservationActionState actionState, Reservation reservation) {}

  private record ChainOrderLookup(ReservationEscrowOrderView order, RuntimeException failure) {

    private static ChainOrderLookup notLoaded() {
      return new ChainOrderLookup(null, null);
    }

    private static ChainOrderLookup loaded(ReservationEscrowOrderView order) {
      return new ChainOrderLookup(order, null);
    }

    private static ChainOrderLookup failed(RuntimeException failure) {
      return new ChainOrderLookup(null, failure);
    }
  }

  private record ChainTerminalOutcome(
      ReservationStatus status, ReservationEscrowStatus escrowStatus) {}
}
