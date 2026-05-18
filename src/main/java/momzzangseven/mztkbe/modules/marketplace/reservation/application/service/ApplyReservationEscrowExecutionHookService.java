package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionConfirmedCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionTerminatedCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ApplyReservationEscrowExecutionHookUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RecordTrainerStrikePort;
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
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.TrainerStrikeEvent;

@RequiredArgsConstructor
@Slf4j
public class ApplyReservationEscrowExecutionHookService
    implements ApplyReservationEscrowExecutionHookUseCase {

  private static final String PURCHASE = "MARKETPLACE_CLASS_PURCHASE";
  private static final String CANCEL = "MARKETPLACE_CLASS_CANCEL";
  private static final String CONFIRM = "MARKETPLACE_CLASS_CONFIRM";
  private static final String EXPIRED_REFUND = "MARKETPLACE_CLASS_EXPIRED_REFUND";
  private static final String TRAINER = "TRAINER";

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

  public void setTransactionPort(RunReservationTransactionPort transactionPort) {
    this.transactionPort = java.util.Objects.requireNonNull(transactionPort);
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
    ChainOrderLookup chainOrderLookup = loadChainOrderBeforeReservationLock(command);
    Reservation reservation = loadReservationForHook(command.executionIntentPublicId(), command);
    if (isAlreadyApplied(reservation, command.actionType(), command.actorType())) {
      Reservation repaired = repairTxHashIfNeeded(reservation, command.txHash());
      syncEscrowProjection(repaired, command.txHash(), null, null);
      markActionStateConfirmed(command, repaired);
      recordTrainerRejectStrikeIfNeeded(reservation, command.actionType(), command.actorType());
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
          default -> throw new IllegalStateException("unsupported marketplace action");
        };
    saveReservationPort.save(updated);
    syncEscrowProjection(updated, command.txHash(), null, null);
    markActionStateConfirmed(command, updated);
    recordTrainerRejectStrikeIfNeeded(updated, command.actionType(), command.actorType());
  }

  @Override
  public void afterExecutionTerminated(ReservationEscrowExecutionTerminatedCommand command) {
    Reservation reservation = loadReservationForHook(command.executionIntentPublicId(), command);
    if (!isCurrentOrRecoverableOrphan(
        command.executionIntentPublicId(), command.pendingAttemptToken(), reservation)) {
      return;
    }
    Reservation updated =
        PURCHASE.equals(command.actionType())
            ? reservation.markPaymentFailed(command.terminalStatus(), command.failureReason())
            : reservation.rollbackToPriorState();
    saveReservationPort.save(updated);
    syncEscrowProjection(updated, null, command.terminalStatus(), command.failureReason());
    markActionStateTerminated(command, updated);
    markPurchaseCreateIdempotencyFailedIfNeeded(command, reservation);
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
              || reservation.getStatus() == ReservationStatus.DEADLINE_RECOVERY_REQUIRED;
      case CANCEL ->
          TRAINER.equals(actorType)
              ? reservation.getStatus() == ReservationStatus.REJECTED
              : reservation.getStatus() == ReservationStatus.USER_CANCELLED;
      case CONFIRM -> reservation.getStatus() == ReservationStatus.SETTLED;
      case EXPIRED_REFUND -> reservation.getStatus() == ReservationStatus.DEADLINE_REFUNDED;
      default -> false;
    };
  }

  private void recordTrainerRejectStrikeIfNeeded(
      Reservation reservation, String actionType, String actorType) {
    if (!CANCEL.equals(actionType)
        || !TRAINER.equals(actorType)
        || recordTrainerStrikePort == null) {
      return;
    }
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
    return transactionPort.notSupported(supplier);
  }

  private void markPurchaseCreateIdempotencyFailedIfNeeded(
      ReservationEscrowExecutionTerminatedCommand command, Reservation reservation) {
    if (!PURCHASE.equals(command.actionType())
        || loadReservationCreateIdempotencyPort == null
        || saveReservationCreateIdempotencyPort == null) {
      return;
    }
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
      ReservationEscrowExecutionTerminatedCommand command, Reservation reservation) {
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
                        .retryable(isRetryableTerminal(command.terminalStatus()))
                        .errorCode(command.terminalStatus())
                        .errorReason(command.failureReason())
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
        && (pendingAttemptToken == null
            || pendingAttemptToken.equals(actionState.getAttemptToken()));
  }

  private boolean isRetryableTerminal(String terminalStatus) {
    return "FAILED_ONCHAIN".equals(terminalStatus)
        || "EXPIRED".equals(terminalStatus)
        || "NONCE_STALE".equals(terminalStatus);
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
}
