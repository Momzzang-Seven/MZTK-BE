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
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RecordTrainerStrikePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.TrainerStrikeEvent;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
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
  @Nullable private final RecordTrainerStrikePort recordTrainerStrikePort;
  @Nullable private final LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;
  @Nullable private final LoadReservationCreateIdempotencyPort loadReservationCreateIdempotencyPort;
  @Nullable private final SaveReservationCreateIdempotencyPort saveReservationCreateIdempotencyPort;

  @Override
  public void afterExecutionConfirmed(ReservationEscrowExecutionConfirmedCommand command) {
    ChainOrderLookup chainOrderLookup = loadChainOrderBeforeReservationLock(command);
    Reservation reservation = loadReservationForHook(command.executionIntentPublicId(), command);
    if (isAlreadyApplied(reservation, command.actionType(), command.actorType())) {
      repairTxHashIfNeeded(reservation, command.txHash());
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
      saveReservationPort.save(
          reservation.markDeadlineSyncRequired(
              "CHAIN_ORDER_READ_FAILED", chainOrderLookup.failure().getMessage()));
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
    recordTrainerStrikePort.recordStrike(
        reservation.getTrainerId(),
        TrainerStrikeEvent.REASON_REJECT,
        RecordTrainerStrikePort.SOURCE_MARKETPLACE_RESERVATION_REJECT,
        String.valueOf(reservation.getId()));
  }

  private Reservation applyPurchaseConfirmed(
      Reservation reservation,
      ReservationEscrowExecutionConfirmedCommand command,
      @Nullable ReservationEscrowOrderView chainOrder) {
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
          TRAINER.equals(command.actorType())
              ? reservation.syncChainOutcome(
                  ReservationStatus.REJECTED,
                  ReservationEscrowStatus.REFUNDED,
                  command.txHash(),
                  order.deadlineEpochSeconds(),
                  deadlineAt)
              : reservation.syncChainOutcome(
                  ReservationStatus.USER_CANCELLED,
                  ReservationEscrowStatus.REFUNDED,
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
      return ChainOrderLookup.loaded(loadReservationEscrowOrderPort.getOrder(command.orderKey()));
    } catch (RuntimeException e) {
      log.warn(
          "Failed to load marketplace chain order before reservation hook lock: reservationId={}, orderKey={}",
          command.reservationId(),
          command.orderKey(),
          e);
      return ChainOrderLookup.failed(e);
    }
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

  private void repairTxHashIfNeeded(Reservation reservation, @Nullable String txHash) {
    if (txHash == null || txHash.equals(reservation.getTxHash())) {
      return;
    }
    saveReservationPort.save(reservation.updateTxHash(txHash));
  }

  private LocalDateTime deadlineAt(Long epochSeconds) {
    if (epochSeconds == null) {
      return null;
    }
    return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), clock.getZone());
  }

  private record ChainOrderLookup(
      @Nullable ReservationEscrowOrderView order, @Nullable RuntimeException failure) {

    private static ChainOrderLookup notLoaded() {
      return new ChainOrderLookup(null, null);
    }

    private static ChainOrderLookup loaded(@Nullable ReservationEscrowOrderView order) {
      return new ChainOrderLookup(order, null);
    }

    private static ChainOrderLookup failed(RuntimeException failure) {
      return new ChainOrderLookup(null, failure);
    }
  }
}
