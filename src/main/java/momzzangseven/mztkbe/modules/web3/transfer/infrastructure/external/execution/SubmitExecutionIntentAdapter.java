package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.external.execution;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraft;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionActionTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceStatusCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionDraft;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferSignRequestBundle;
import momzzangseven.mztkbe.modules.web3.transfer.application.dto.TransferUnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.SubmitExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionMode;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(CreateExecutionIntentUseCase.class)
@RequiredArgsConstructor
/** Adapter that bridges transfer draft submission to shared execution create use case. */
public class SubmitExecutionIntentAdapter implements SubmitExecutionDraftPort {

  private final CreateExecutionIntentUseCase createExecutionIntentUseCase;

  /**
   * Delegates draft submission to execution module without transfer-module coupling to service
   * impl.
   */
  @Override
  public TransferExecutionIntentResult submit(TransferExecutionDraft draft) {
    CreateExecutionIntentResult result =
        createExecutionIntentUseCase.execute(
            new CreateExecutionIntentCommand(toExecutionDraft(draft)));
    return new TransferExecutionIntentResult(
        TransferExecutionResourceType.valueOf(result.resourceType().name()),
        result.resourceId(),
        TransferExecutionResourceStatus.valueOf(result.resourceStatus().name()),
        result.executionIntentId(),
        TransferExecutionIntentStatus.valueOf(result.executionIntentStatus().name()),
        result.expiresAt(),
        TransferExecutionMode.valueOf(result.mode().name()),
        result.signCount(),
        toTransferSignRequest(result.signRequest()),
        result.existing(),
        null,
        null,
        null);
  }

  private ExecutionDraft toExecutionDraft(TransferExecutionDraft draft) {
    return new ExecutionDraft(
        ExecutionResourceTypeCode.valueOf(draft.resourceType().name()),
        draft.resourceId(),
        ExecutionResourceStatusCode.valueOf(draft.resourceStatus().name()),
        ExecutionActionTypeCode.valueOf(draft.actionType().name()),
        draft.requesterUserId(),
        draft.counterpartyUserId(),
        draft.rootIdempotencyKey(),
        draft.payloadHash(),
        draft.payloadSnapshotJson(),
        mapCalls(draft.calls()),
        draft.fallbackAllowed(),
        draft.authorityAddress(),
        draft.authorityNonce(),
        draft.delegateTarget(),
        draft.authorizationPayloadHash(),
        toUnsignedTxSnapshot(draft.unsignedTxSnapshot()),
        draft.unsignedTxFingerprint(),
        draft.expiresAt());
  }

  private List<ExecutionDraftCall> mapCalls(List<TransferExecutionDraftCall> calls) {
    return calls.stream()
        .map(call -> new ExecutionDraftCall(call.target(), call.value(), call.data()))
        .toList();
  }

  private UnsignedTxSnapshot toUnsignedTxSnapshot(TransferUnsignedTxSnapshot snapshot) {
    if (snapshot == null) {
      return null;
    }
    return new UnsignedTxSnapshot(
        snapshot.chainId(),
        snapshot.fromAddress(),
        snapshot.toAddress(),
        snapshot.value(),
        snapshot.data(),
        snapshot.nonce(),
        snapshot.gasLimit(),
        snapshot.maxPriorityFeePerGas(),
        snapshot.maxFeePerGas());
  }

  private TransferSignRequestBundle toTransferSignRequest(SignRequestBundle signRequest) {
    if (signRequest == null) {
      return null;
    }
    if (signRequest.authorization() != null || signRequest.submit() != null) {
      return TransferSignRequestBundle.forEip7702(
          new TransferSignRequestBundle.AuthorizationSignRequest(
              signRequest.authorization().chainId(),
              signRequest.authorization().delegateTarget(),
              signRequest.authorization().authorityNonce(),
              signRequest.authorization().payloadHashToSign()),
          new TransferSignRequestBundle.SubmitSignRequest(
              signRequest.submit().executionDigest(), signRequest.submit().deadlineEpochSeconds()));
    }
    return TransferSignRequestBundle.forEip1559(
        new TransferSignRequestBundle.TransactionSignRequest(
            signRequest.transaction().chainId(),
            signRequest.transaction().fromAddress(),
            signRequest.transaction().toAddress(),
            signRequest.transaction().valueHex(),
            signRequest.transaction().data(),
            signRequest.transaction().nonce(),
            signRequest.transaction().gasLimitHex(),
            signRequest.transaction().maxPriorityFeePerGasHex(),
            signRequest.transaction().maxFeePerGasHex(),
            signRequest.transaction().expectedNonce()));
  }
}
