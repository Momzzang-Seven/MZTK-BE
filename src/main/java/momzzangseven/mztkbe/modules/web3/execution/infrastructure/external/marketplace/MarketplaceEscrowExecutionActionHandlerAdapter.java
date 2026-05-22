package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.marketplace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionConfirmedCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionTerminatedCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionTerminatedCommand.ReservationEscrowExecutionTerminationEvidence;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ApplyReservationEscrowExecutionHookUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTerminationEvidenceView;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowExecutionPayload;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
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
          ExecutionActionType.MARKETPLACE_CLASS_EXPIRED_REFUND,
          ExecutionActionType.MARKETPLACE_ADMIN_REFUND,
          ExecutionActionType.MARKETPLACE_ADMIN_SETTLE);

  private final ObjectMapper objectMapper;
  private final ApplyReservationEscrowExecutionHookUseCase
      applyReservationEscrowExecutionHookUseCase;
  @Nullable private LoadExecutionTransactionPort loadExecutionTransactionPort;
  @Nullable private LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;

  @Autowired(required = false)
  void setLoadExecutionTransactionPort(LoadExecutionTransactionPort loadExecutionTransactionPort) {
    this.loadExecutionTransactionPort = loadExecutionTransactionPort;
  }

  @Autowired(required = false)
  void setLoadReservationEscrowOrderPort(
      LoadReservationEscrowOrderPort loadReservationEscrowOrderPort) {
    this.loadReservationEscrowOrderPort = loadReservationEscrowOrderPort;
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
    applyReservationEscrowExecutionHookUseCase.afterExecutionConfirmed(
        new ReservationEscrowExecutionConfirmedCommand(
            intent.getPublicId(),
            txHash(intent),
            payload.actionType().name(),
            actorType(payload),
            payload.reasonCode(),
            payload.reservationId(),
            payload.orderKey(),
            payload.expectedContractDeadlineEpochSeconds(),
            payload.contractDeadlineEpochSeconds(),
            payload.sessionEndAt(),
            payload.pendingAttemptToken(),
            payload.actionStateId()));
  }

  @Override
  public void afterExecutionFailedOnchain(
      ExecutionIntent intent, ExecutionActionPlan actionPlan, String failureReason) {
    // Marketplace local rollback is centralized in afterExecutionTerminated.
  }

  @Override
  public void afterExecutionTerminated(
      ExecutionIntent intent,
      ExecutionActionPlan actionPlan,
      ExecutionIntentStatus terminalStatus,
      String failureReason) {
    afterExecutionTerminated(
        intent,
        actionPlan,
        terminalStatus,
        failureReason,
        buildTerminationEvidence(intent, actionPlan, terminalStatus, failureReason));
  }

  @Override
  public void afterExecutionTerminated(
      ExecutionIntent intent,
      ExecutionActionPlan actionPlan,
      ExecutionIntentStatus terminalStatus,
      String failureReason,
      ExecutionTerminationEvidenceView evidence) {
    MarketplaceEscrowExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    applyReservationEscrowExecutionHookUseCase.afterExecutionTerminated(
        new ReservationEscrowExecutionTerminatedCommand(
            intent.getPublicId(),
            payload.actionType().name(),
            actorType(payload),
            payload.reservationId(),
            payload.pendingAttemptToken(),
            payload.actionStateId(),
            terminalStatus.name(),
            failureReason,
            payload.reasonCode(),
            toReservationEvidence(evidence)));
  }

  @Override
  public ExecutionTerminationEvidenceView buildTerminationEvidence(
      ExecutionIntent intent,
      ExecutionActionPlan actionPlan,
      ExecutionIntentStatus terminalStatus,
      String failureReason) {
    MarketplaceEscrowExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    ExecutionTransactionSummary transaction = transaction(intent);
    String txHash = transaction == null ? null : transaction.txHash();
    String transactionStatus = transaction == null ? null : transaction.status().name();
    ChainEvidence chainEvidence = chainEvidence(payload);
    return new ExecutionTerminationEvidenceView(
        txHash,
        txHash != null && !txHash.isBlank(),
        intent.getPublicId(),
        null,
        transactionStatus,
        receiptStatus(terminalStatus, transactionStatus, txHash),
        chainEvidence.chainOrderState(),
        chainEvidence.evidenceErrorCode(),
        LocalDateTime.now());
  }

  private ExecutionReferenceType referenceType(MarketplaceExecutionActionType actionType) {
    return switch (actionType) {
      case MARKETPLACE_CLASS_CONFIRM, MARKETPLACE_ADMIN_SETTLE ->
          ExecutionReferenceType.USER_TO_USER;
      case MARKETPLACE_CLASS_CANCEL, MARKETPLACE_CLASS_EXPIRED_REFUND ->
          ExecutionReferenceType.SERVER_TO_USER;
      case MARKETPLACE_ADMIN_REFUND -> ExecutionReferenceType.SERVER_TO_USER;
      case MARKETPLACE_CLASS_PURCHASE -> ExecutionReferenceType.USER_TO_SERVER;
    };
  }

  private String actorType(MarketplaceEscrowExecutionPayload payload) {
    if (payload.actorType() != null) {
      return payload.actorType().name();
    }
    if (payload.adminProvenanceActor() != null) {
      return payload.adminProvenanceActor().name();
    }
    return payload.requestSource();
  }

  @Nullable
  private String txHash(ExecutionIntent intent) {
    ExecutionTransactionSummary transaction = transaction(intent);
    return transaction == null ? null : transaction.txHash();
  }

  @Nullable
  private ExecutionTransactionSummary transaction(ExecutionIntent intent) {
    if (loadExecutionTransactionPort == null || intent.getSubmittedTxId() == null) {
      return null;
    }
    return loadExecutionTransactionPort.findById(intent.getSubmittedTxId()).orElse(null);
  }

  private ChainEvidence chainEvidence(MarketplaceEscrowExecutionPayload payload) {
    if (!isAdminAction(payload.actionType())) {
      return new ChainEvidence(null, null);
    }
    if (loadReservationEscrowOrderPort == null) {
      return new ChainEvidence("UNKNOWN", "CHAIN_ORDER_EVIDENCE_UNAVAILABLE");
    }
    try {
      ReservationEscrowOrderView order =
          loadReservationEscrowOrderPort.getOrder(payload.orderKey());
      if (order == null || order.isAbsent()) {
        return new ChainEvidence("ABSENT", null);
      }
      return switch (order.state()) {
        case ReservationEscrowOrderView.STATE_CREATED -> new ChainEvidence("CREATED", null);
        case ReservationEscrowOrderView.STATE_CONFIRMED -> new ChainEvidence("CONFIRMED", null);
        case ReservationEscrowOrderView.STATE_ADMIN_SETTLED ->
            new ChainEvidence("ADMIN_SETTLED", null);
        case ReservationEscrowOrderView.STATE_CANCELLED -> new ChainEvidence("CANCELLED", null);
        case ReservationEscrowOrderView.STATE_ADMIN_REFUNDED ->
            new ChainEvidence("ADMIN_REFUNDED", null);
        case ReservationEscrowOrderView.STATE_DEADLINE_REFUNDED ->
            new ChainEvidence("DEADLINE_REFUNDED", null);
        default -> new ChainEvidence("UNKNOWN", "CHAIN_ORDER_STATE_UNSUPPORTED");
      };
    } catch (RuntimeException ex) {
      log.warn(
          "failed to build marketplace admin termination chain evidence: reservationId={}, orderKey={}",
          payload.reservationId(),
          payload.orderKey(),
          ex);
      return new ChainEvidence("UNKNOWN", "CHAIN_ORDER_LOOKUP_FAILED");
    }
  }

  private String receiptStatus(
      ExecutionIntentStatus terminalStatus, String transactionStatus, @Nullable String txHash) {
    if (txHash == null || txHash.isBlank()) {
      return "MISSING";
    }
    if ("SUCCEEDED".equals(transactionStatus)
        || terminalStatus == ExecutionIntentStatus.CONFIRMED) {
      return "SUCCESS";
    }
    if ("FAILED_ONCHAIN".equals(transactionStatus)
        || terminalStatus == ExecutionIntentStatus.FAILED_ONCHAIN) {
      return "REVERTED";
    }
    if ("UNCONFIRMED".equals(transactionStatus)) {
      return "UNKNOWN";
    }
    return "UNKNOWN";
  }

  private ReservationEscrowExecutionTerminationEvidence toReservationEvidence(
      ExecutionTerminationEvidenceView evidence) {
    if (evidence == null) {
      return null;
    }
    return new ReservationEscrowExecutionTerminationEvidence(
        evidence.txHash(),
        evidence.hasTxHash(),
        evidence.executionIntentPublicId(),
        evidence.actionStateExecutionIntentPublicId(),
        evidence.executionTransactionStatus(),
        evidence.receiptStatus(),
        evidence.chainOrderState(),
        evidence.evidenceErrorCode(),
        evidence.evidenceCheckedAt());
  }

  private boolean isAdminAction(MarketplaceExecutionActionType actionType) {
    return actionType == MarketplaceExecutionActionType.MARKETPLACE_ADMIN_REFUND
        || actionType == MarketplaceExecutionActionType.MARKETPLACE_ADMIN_SETTLE;
  }

  private MarketplaceEscrowExecutionPayload readPayload(String payloadSnapshotJson) {
    try {
      return objectMapper.readValue(payloadSnapshotJson, MarketplaceEscrowExecutionPayload.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "failed to deserialize marketplace escrow execution payload", e);
    }
  }

  private record ChainEvidence(String chainOrderState, String evidenceErrorCode) {}
}
