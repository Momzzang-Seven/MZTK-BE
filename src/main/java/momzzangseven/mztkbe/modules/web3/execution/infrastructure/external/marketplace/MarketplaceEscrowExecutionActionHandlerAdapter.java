package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.marketplace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionConfirmedCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionTerminatedCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ApplyReservationEscrowExecutionHookUseCase;
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
  private final ApplyReservationEscrowExecutionHookUseCase
      applyReservationEscrowExecutionHookUseCase;
  @Nullable private LoadExecutionTransactionPort loadExecutionTransactionPort;

  @Autowired(required = false)
  void setLoadExecutionTransactionPort(LoadExecutionTransactionPort loadExecutionTransactionPort) {
    this.loadExecutionTransactionPort = loadExecutionTransactionPort;
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
            payload.actorType().name(),
            payload.reservationId(),
            payload.orderKey(),
            payload.expectedContractDeadlineEpochSeconds(),
            payload.contractDeadlineEpochSeconds(),
            payload.sessionEndAt(),
            payload.pendingAttemptToken()));
  }

  @Override
  public void afterExecutionTerminated(
      ExecutionIntent intent,
      ExecutionActionPlan actionPlan,
      ExecutionIntentStatus terminalStatus,
      String failureReason) {
    MarketplaceEscrowExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    applyReservationEscrowExecutionHookUseCase.afterExecutionTerminated(
        new ReservationEscrowExecutionTerminatedCommand(
            intent.getPublicId(),
            payload.actionType().name(),
            payload.actorType().name(),
            payload.reservationId(),
            payload.pendingAttemptToken(),
            terminalStatus.name(),
            failureReason));
  }

  private ExecutionReferenceType referenceType(MarketplaceExecutionActionType actionType) {
    return switch (actionType) {
      case MARKETPLACE_CLASS_CONFIRM -> ExecutionReferenceType.USER_TO_USER;
      case MARKETPLACE_CLASS_CANCEL, MARKETPLACE_CLASS_EXPIRED_REFUND ->
          ExecutionReferenceType.SERVER_TO_USER;
      case MARKETPLACE_CLASS_PURCHASE -> ExecutionReferenceType.USER_TO_SERVER;
    };
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

  private MarketplaceEscrowExecutionPayload readPayload(String payloadSnapshotJson) {
    try {
      return objectMapper.readValue(payloadSnapshotJson, MarketplaceEscrowExecutionPayload.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "failed to deserialize marketplace escrow execution payload", e);
    }
  }
}
