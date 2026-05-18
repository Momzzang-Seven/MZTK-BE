package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.wallet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionActionPlan;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionActionHandlerPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionReferenceType;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionTransactionStatus;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.FinalizeWalletRegistrationCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.MarkWalletRegistrationApprovalSubmittedCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.MarkWalletRegistrationApprovalTerminatedCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ValidateWalletRegistrationApprovalExecutionCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionPayload;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.FinalizeWalletRegistrationUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.MarkWalletRegistrationApprovalSubmittedUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.MarkWalletRegistrationApprovalTerminatedUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.ValidateWalletRegistrationApprovalExecutionUseCase;
import org.springframework.stereotype.Component;

/** Execution action handler for wallet-registration escrow approval intents. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletApprovalExecutionActionHandlerAdapter implements ExecutionActionHandlerPort {

  private final ObjectMapper objectMapper;
  private final ValidateWalletRegistrationApprovalExecutionUseCase validateApprovalExecutionUseCase;
  private final MarkWalletRegistrationApprovalSubmittedUseCase markApprovalSubmittedUseCase;
  private final FinalizeWalletRegistrationUseCase finalizeWalletRegistrationUseCase;
  private final MarkWalletRegistrationApprovalTerminatedUseCase markApprovalTerminatedUseCase;

  @Override
  public boolean supports(ExecutionActionType actionType) {
    return actionType == ExecutionActionType.WALLET_ESCROW_APPROVE;
  }

  @Override
  public boolean supports(ExecutionIntent intent) {
    if (!supports(intent.getActionType())
        || intent.getResourceType() != ExecutionResourceType.WALLET_REGISTRATION) {
      return false;
    }
    try {
      WalletApprovalExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
      return payload.registrationId() != null
          && payload.registrationId().equals(intent.getResourceId());
    } catch (RuntimeException exception) {
      return false;
    }
  }

  @Override
  public ExecutionActionPlan buildActionPlan(ExecutionIntent intent) {
    WalletApprovalExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    List<ExecutionDraftCall> calls =
        payload.approvals().stream()
            .map(
                approval ->
                    new ExecutionDraftCall(
                        approval.callTarget(), BigInteger.ZERO, approval.callData()))
            .toList();
    return new ExecutionActionPlan(BigInteger.ZERO, ExecutionReferenceType.USER_TO_SERVER, calls);
  }

  @Override
  public void beforeExecute(ExecutionIntent intent, ExecutionActionPlan actionPlan) {
    WalletApprovalExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
    validateApprovalExecutionUseCase.execute(
        new ValidateWalletRegistrationApprovalExecutionCommand(
            payload.registrationId(), intent.getPublicId(), intent.getRequesterUserId()));
  }

  @Override
  public void afterTransactionSubmitted(
      ExecutionIntent intent, ExecutionActionPlan actionPlan, ExecutionTransactionStatus txStatus) {
    try {
      WalletApprovalExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
      markApprovalSubmittedUseCase.execute(
          new MarkWalletRegistrationApprovalSubmittedCommand(
              payload.registrationId(), intent.getPublicId(), txStatus.name()));
    } catch (RuntimeException exception) {
      log.error(
          "Failed to sync wallet registration after approval tx submission: executionIntentId={}",
          intent.getPublicId(),
          exception);
    }
  }

  @Override
  public void afterExecutionConfirmed(ExecutionIntent intent, ExecutionActionPlan actionPlan) {
    try {
      WalletApprovalExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
      finalizeWalletRegistrationUseCase.execute(
          new FinalizeWalletRegistrationCommand(payload.registrationId(), intent.getPublicId()));
    } catch (RuntimeException exception) {
      log.error(
          "Failed to finalize wallet registration after approval confirmation: executionIntentId={}",
          intent.getPublicId(),
          exception);
    }
  }

  @Override
  public void afterExecutionTerminated(
      ExecutionIntent intent,
      ExecutionActionPlan actionPlan,
      ExecutionIntentStatus terminalStatus,
      String failureReason) {
    try {
      WalletApprovalExecutionPayload payload = readPayload(intent.getPayloadSnapshotJson());
      markApprovalTerminatedUseCase.execute(
          new MarkWalletRegistrationApprovalTerminatedCommand(
              payload.registrationId(),
              intent.getPublicId(),
              terminalStatus.name(),
              failureReason));
    } catch (RuntimeException exception) {
      log.error(
          "Failed to sync wallet registration after approval termination: executionIntentId={}",
          intent.getPublicId(),
          exception);
    }
  }

  private WalletApprovalExecutionPayload readPayload(String payloadSnapshotJson) {
    try {
      return objectMapper.readValue(payloadSnapshotJson, WalletApprovalExecutionPayload.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("failed to deserialize wallet approval payload", exception);
    }
  }
}
