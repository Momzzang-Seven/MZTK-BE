package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RecordTrainerStrikePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.TrainerStrikeEvent;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceEscrowOrderPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.MarketplaceEscrowOrderView;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceActorType;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnAnyExecutionEnabled
public class MarketplaceEscrowExecutionActionHandlerAdapter implements ExecutionActionHandlerPort {

  private static final EnumSet<ExecutionActionType> SUPPORTED_ACTIONS =
      EnumSet.of(
          ExecutionActionType.MARKETPLACE_CLASS_PURCHASE,
          ExecutionActionType.MARKETPLACE_CLASS_CANCEL,
          ExecutionActionType.MARKETPLACE_CLASS_CONFIRM,
          ExecutionActionType.MARKETPLACE_CLASS_EXPIRED_REFUND);

  private final ObjectMapper objectMapper;
  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final Clock clock;
  @Nullable private RecordTrainerStrikePort recordTrainerStrikePort;
  @Nullable private LoadMarketplaceEscrowOrderPort loadMarketplaceEscrowOrderPort;
  @Nullable private LoadExecutionTransactionPort loadExecutionTransactionPort;
  @Nullable private LoadReservationCreateIdempotencyPort loadReservationCreateIdempotencyPort;
  @Nullable private SaveReservationCreateIdempotencyPort saveReservationCreateIdempotencyPort;

  @Autowired(required = false)
  void setRecordTrainerStrikePort(RecordTrainerStrikePort recordTrainerStrikePort) {
    this.recordTrainerStrikePort = recordTrainerStrikePort;
  }

  @Autowired(required = false)
  void setLoadMarketplaceEscrowOrderPort(
      LoadMarketplaceEscrowOrderPort loadMarketplaceEscrowOrderPort) {
    this.loadMarketplaceEscrowOrderPort = loadMarketplaceEscrowOrderPort;
  }

  @Autowired(required = false)
  void setLoadExecutionTransactionPort(LoadExecutionTransactionPort loadExecutionTransactionPort) {
    this.loadExecutionTransactionPort = loadExecutionTransactionPort;
  }

  @Autowired(required = false)
  void setReservationCreateIdempotencyPorts(
      LoadReservationCreateIdempotencyPort loadReservationCreateIdempotencyPort,
      SaveReservationCreateIdempotencyPort saveReservationCreateIdempotencyPort) {
    this.loadReservationCreateIdempotencyPort = loadReservationCreateIdempotencyPort;
    this.saveReservationCreateIdempotencyPort = saveReservationCreateIdempotencyPort;
  }

  @Override
  public boolean supports(ExecutionActionType actionType) {
    return SUPPORTED_ACTIONS.contains(actionType);
  }

  @Override
  public ExecutionActionPlan buildActionPlan(ExecutionIntent intent) {
    MarketplaceEscrowExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    return new ExecutionActionPlan(
        payload.priceBaseUnits(),
        referenceType(payload.actionType()),
        List.of(new ExecutionDraftCall(payload.callTarget(), BigInteger.ZERO, payload.callData())));
  }

  @Override
  public void afterExecutionConfirmed(ExecutionIntent intent, ExecutionActionPlan actionPlan) {
    MarketplaceEscrowExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    ChainOrderLookup chainOrderLookup = loadChainOrderBeforeReservationLock(payload);
    Reservation reservation = loadReservationForHook(intent, payload);
    String txHash = txHash(intent);
    if (isAlreadyApplied(reservation, payload)) {
      repairTxHashIfNeeded(reservation, txHash);
      recordTrainerRejectStrikeIfNeeded(reservation, payload);
      return;
    }
    if (!isCurrentOrRecoverableOrphan(intent, payload, reservation)) {
      log.warn(
          "Skipping stale marketplace confirmation: intentId={}, reservationId={}",
          intent.getPublicId(),
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
        switch (payload.actionType()) {
          case MARKETPLACE_CLASS_PURCHASE ->
              applyPurchaseConfirmed(reservation, payload, txHash, chainOrderLookup.order());
          case MARKETPLACE_CLASS_CANCEL ->
              payload.actorType() == MarketplaceActorType.TRAINER
                  ? reservation.reject(txHash, reservation.getRejectionReason())
                  : reservation.cancelByUser(txHash);
          case MARKETPLACE_CLASS_CONFIRM -> reservation.complete(txHash);
          case MARKETPLACE_CLASS_EXPIRED_REFUND -> reservation.markDeadlineRefunded(txHash);
        };
    saveReservationPort.save(updated);
    recordTrainerRejectStrikeIfNeeded(updated, payload);
  }

  @Override
  public void afterExecutionTerminated(
      ExecutionIntent intent,
      ExecutionActionPlan actionPlan,
      ExecutionIntentStatus terminalStatus,
      String failureReason) {
    MarketplaceEscrowExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    Reservation reservation = loadReservationForHook(intent, payload);
    if (!isCurrentOrRecoverableOrphan(intent, payload, reservation)) {
      return;
    }
    Reservation updated =
        payload.actionType() == MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE
            ? reservation.markPaymentFailed(terminalStatus.name(), failureReason)
            : reservation.rollbackToPriorState();
    saveReservationPort.save(updated);
    markPurchaseCreateIdempotencyFailedIfNeeded(payload, reservation, terminalStatus);
  }

  private Reservation loadReservationForHook(
      ExecutionIntent intent, MarketplaceEscrowExecutionPayload payload) {
    return loadReservationPort
        .findByCurrentExecutionIntentPublicIdWithLock(intent.getPublicId())
        .or(() -> loadReservationPort.findByIdWithLock(payload.reservationId()))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "marketplace reservation not found for intentId=" + intent.getPublicId()));
  }

  private boolean isCurrentOrRecoverableOrphan(
      ExecutionIntent intent, MarketplaceEscrowExecutionPayload payload, Reservation reservation) {
    String currentIntentId = reservation.getCurrentExecutionIntentPublicId();
    if (intent.getPublicId().equals(currentIntentId)) {
      return true;
    }
    if (currentIntentId != null && !currentIntentId.isBlank()) {
      return false;
    }
    return payload.pendingAttemptToken() != null
        && payload.pendingAttemptToken().equals(reservation.getPendingAttemptToken());
  }

  private ExecutionReferenceType referenceType(MarketplaceExecutionActionType actionType) {
    return switch (actionType) {
      case MARKETPLACE_CLASS_CONFIRM -> ExecutionReferenceType.USER_TO_USER;
      case MARKETPLACE_CLASS_CANCEL, MARKETPLACE_CLASS_EXPIRED_REFUND ->
          ExecutionReferenceType.SERVER_TO_USER;
      case MARKETPLACE_CLASS_PURCHASE -> ExecutionReferenceType.USER_TO_SERVER;
    };
  }

  private boolean isAlreadyApplied(
      Reservation reservation, MarketplaceEscrowExecutionPayload payload) {
    return switch (payload.actionType()) {
      case MARKETPLACE_CLASS_PURCHASE ->
          reservation.getStatus() == ReservationStatus.PENDING
              || reservation.getStatus() == ReservationStatus.DEADLINE_RECOVERY_REQUIRED;
      case MARKETPLACE_CLASS_CANCEL ->
          payload.actorType() == MarketplaceActorType.TRAINER
              ? reservation.getStatus() == ReservationStatus.REJECTED
              : reservation.getStatus() == ReservationStatus.USER_CANCELLED;
      case MARKETPLACE_CLASS_CONFIRM -> reservation.getStatus() == ReservationStatus.SETTLED;
      case MARKETPLACE_CLASS_EXPIRED_REFUND ->
          reservation.getStatus() == ReservationStatus.DEADLINE_REFUNDED;
    };
  }

  private void recordTrainerRejectStrikeIfNeeded(
      Reservation reservation, MarketplaceEscrowExecutionPayload payload) {
    if (payload.actionType() != MarketplaceExecutionActionType.MARKETPLACE_CLASS_CANCEL
        || payload.actorType() != MarketplaceActorType.TRAINER
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
      MarketplaceEscrowExecutionPayload payload,
      String txHash,
      @Nullable MarketplaceEscrowOrderView chainOrder) {
    if (chainOrder != null) {
      return applyPurchaseChainState(reservation, payload, chainOrder, txHash);
    }
    Long deadlineEpochSeconds =
        payload.contractDeadlineEpochSeconds() == null
            ? payload.expectedContractDeadlineEpochSeconds()
            : payload.contractDeadlineEpochSeconds();
    LocalDateTime deadlineAt = deadlineAt(deadlineEpochSeconds);
    if (deadlineAt != null && payload.sessionEndAt().plusHours(24).isAfter(deadlineAt)) {
      return reservation.markPurchaseDeadlineRecoveryRequired(
          deadlineEpochSeconds, deadlineAt, txHash);
    }
    return reservation.markPurchaseConfirmedLocked(deadlineEpochSeconds, deadlineAt, txHash);
  }

  private Reservation applyPurchaseChainState(
      Reservation reservation,
      MarketplaceEscrowExecutionPayload payload,
      MarketplaceEscrowOrderView order,
      String txHash) {
    if (order.isAbsent()) {
      return reservation.markDeadlineSyncRequired(
          "ORDER_ABSENT_AFTER_CONFIRMED",
          "confirmed marketplace purchase intent has no matching on-chain order");
    }
    LocalDateTime deadlineAt = deadlineAt(order.deadlineEpochSeconds());
    return switch (order.state()) {
      case MarketplaceEscrowOrderView.STATE_CREATED -> {
        if (deadlineAt != null && payload.sessionEndAt().plusHours(24).isAfter(deadlineAt)) {
          yield reservation.markPurchaseDeadlineRecoveryRequired(
              order.deadlineEpochSeconds(), deadlineAt, txHash);
        }
        yield reservation.markPurchaseConfirmedLocked(
            order.deadlineEpochSeconds(), deadlineAt, txHash);
      }
      case MarketplaceEscrowOrderView.STATE_CONFIRMED ->
          reservation.syncChainOutcome(
              ReservationStatus.SETTLED,
              ReservationEscrowStatus.SETTLED,
              txHash,
              order.deadlineEpochSeconds(),
              deadlineAt);
      case MarketplaceEscrowOrderView.STATE_CANCELLED ->
          payload.actorType() == MarketplaceActorType.TRAINER
              ? reservation.syncChainOutcome(
                  ReservationStatus.REJECTED,
                  ReservationEscrowStatus.REFUNDED,
                  txHash,
                  order.deadlineEpochSeconds(),
                  deadlineAt)
              : reservation.syncChainOutcome(
                  ReservationStatus.USER_CANCELLED,
                  ReservationEscrowStatus.REFUNDED,
                  txHash,
                  order.deadlineEpochSeconds(),
                  deadlineAt);
      case MarketplaceEscrowOrderView.STATE_ADMIN_SETTLED ->
          reservation.syncChainOutcome(
              ReservationStatus.AUTO_SETTLED,
              ReservationEscrowStatus.SETTLED,
              txHash,
              order.deadlineEpochSeconds(),
              deadlineAt);
      case MarketplaceEscrowOrderView.STATE_ADMIN_REFUNDED ->
          reservation.syncChainOutcome(
              ReservationStatus.TIMEOUT_CANCELLED,
              ReservationEscrowStatus.REFUNDED,
              txHash,
              order.deadlineEpochSeconds(),
              deadlineAt);
      case MarketplaceEscrowOrderView.STATE_DEADLINE_REFUNDED ->
          reservation.syncChainOutcome(
              ReservationStatus.DEADLINE_REFUNDED,
              ReservationEscrowStatus.DEADLINE_REFUNDED,
              txHash,
              order.deadlineEpochSeconds(),
              deadlineAt);
      default ->
          reservation.markDeadlineSyncRequired(
              "UNKNOWN_ORDER_STATE", "Unsupported marketplace order state: " + order.state());
    };
  }

  private ChainOrderLookup loadChainOrderBeforeReservationLock(
      MarketplaceEscrowExecutionPayload payload) {
    if (payload.actionType() != MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE
        || loadMarketplaceEscrowOrderPort == null) {
      return ChainOrderLookup.notLoaded();
    }
    try {
      return ChainOrderLookup.loaded(loadMarketplaceEscrowOrderPort.getOrder(payload.orderKey()));
    } catch (RuntimeException e) {
      log.warn(
          "Failed to load marketplace chain order before reservation hook lock: reservationId={}, orderKey={}",
          payload.reservationId(),
          payload.orderKey(),
          e);
      return ChainOrderLookup.failed(e);
    }
  }

  private LocalDateTime deadlineAt(Long epochSeconds) {
    if (epochSeconds == null) {
      return null;
    }
    return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), clock.getZone());
  }

  @Nullable
  private String txHash(ExecutionIntent intent) {
    if (loadExecutionTransactionPort == null || intent.getSubmittedTxId() == null) {
      return null;
    }
    return loadExecutionTransactionPort
        .findById(intent.getSubmittedTxId())
        .map(ExecutionTransactionSummary::txHash)
        .filter(txHash -> txHash != null && !txHash.isBlank())
        .orElse(null);
  }

  private void markPurchaseCreateIdempotencyFailedIfNeeded(
      MarketplaceEscrowExecutionPayload payload,
      Reservation reservation,
      ExecutionIntentStatus terminalStatus) {
    if (payload.actionType() != MarketplaceExecutionActionType.MARKETPLACE_CLASS_PURCHASE
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
                            + terminalStatus.name()
                            + "\"}")));
  }

  private void repairTxHashIfNeeded(Reservation reservation, @Nullable String txHash) {
    if (txHash == null || txHash.equals(reservation.getTxHash())) {
      return;
    }
    saveReservationPort.save(reservation.updateTxHash(txHash));
  }

  private MarketplaceEscrowExecutionPayload readPayload(String payloadSnapshotJson) {
    try {
      return objectMapper.readValue(payloadSnapshotJson, MarketplaceEscrowExecutionPayload.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "failed to deserialize marketplace escrow execution payload", e);
    }
  }

  private record ChainOrderLookup(
      @Nullable MarketplaceEscrowOrderView order, @Nullable RuntimeException failure) {

    private static ChainOrderLookup notLoaded() {
      return new ChainOrderLookup(null, null);
    }

    private static ChainOrderLookup loaded(@Nullable MarketplaceEscrowOrderView order) {
      return new ChainOrderLookup(order, null);
    }

    private static ChainOrderLookup failed(RuntimeException failure) {
      return new ChainOrderLookup(null, failure);
    }
  }
}
