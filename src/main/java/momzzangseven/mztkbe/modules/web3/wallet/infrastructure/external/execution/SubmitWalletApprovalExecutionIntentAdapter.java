package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.execution;

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
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionDraft;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalSignRequestBundle;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletUnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SubmitWalletApprovalExecutionDraftPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
@ConditionalOnBean(CreateExecutionIntentUseCase.class)
public class SubmitWalletApprovalExecutionIntentAdapter
    implements SubmitWalletApprovalExecutionDraftPort {

  private final CreateExecutionIntentUseCase createExecutionIntentUseCase;

  @Override
  public WalletApprovalExecutionIntentResult submit(WalletApprovalExecutionDraft draft) {
    CreateExecutionIntentResult result =
        createExecutionIntentUseCase.execute(
            new CreateExecutionIntentCommand(toExecutionDraft(draft)));
    return toResult(draft.actionType().name(), result);
  }

  private ExecutionDraft toExecutionDraft(WalletApprovalExecutionDraft draft) {
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
        toCalls(draft.calls()),
        draft.fallbackAllowed(),
        draft.authorityAddress(),
        draft.authorityNonce(),
        draft.delegateTarget(),
        draft.authorizationPayloadHash(),
        toUnsignedTxSnapshot(draft.unsignedTxSnapshot()),
        draft.unsignedTxFingerprint(),
        draft.expiresAt());
  }

  private List<ExecutionDraftCall> toCalls(List<WalletApprovalExecutionDraftCall> calls) {
    return calls.stream()
        .map(call -> new ExecutionDraftCall(call.target(), call.value(), call.data()))
        .toList();
  }

  private UnsignedTxSnapshot toUnsignedTxSnapshot(WalletUnsignedTxSnapshot snapshot) {
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

  private WalletApprovalExecutionIntentResult toResult(
      String actionType, CreateExecutionIntentResult result) {
    return new WalletApprovalExecutionIntentResult(
        new WalletApprovalExecutionIntentResult.Resource(
            result.resourceType().name(), result.resourceId(), result.resourceStatus().name()),
        actionType,
        new WalletApprovalExecutionIntentResult.ExecutionIntent(
            result.executionIntentId(),
            result.executionIntentStatus().name(),
            result.expiresAt(),
            result.expiresAtEpochSeconds()),
        new WalletApprovalExecutionIntentResult.Execution(result.mode().name(), result.signCount()),
        toSignRequest(result.signRequest()),
        result.existing());
  }

  private WalletApprovalSignRequestBundle toSignRequest(SignRequestBundle source) {
    if (source == null) {
      return null;
    }
    if (source.authorization() != null && source.submit() != null) {
      return WalletApprovalSignRequestBundle.forEip7702(
          new WalletApprovalSignRequestBundle.AuthorizationSignRequest(
              source.authorization().chainId(),
              source.authorization().delegateTarget(),
              source.authorization().authorityNonce(),
              source.authorization().payloadHashToSign()),
          new WalletApprovalSignRequestBundle.SubmitSignRequest(
              source.submit().executionDigest(), source.submit().deadlineEpochSeconds()));
    }
    if (source.transaction() != null) {
      return WalletApprovalSignRequestBundle.forEip1559(
          new WalletApprovalSignRequestBundle.TransactionSignRequest(
              source.transaction().chainId(),
              source.transaction().fromAddress(),
              source.transaction().toAddress(),
              source.transaction().valueHex(),
              source.transaction().data(),
              source.transaction().nonce(),
              source.transaction().gasLimitHex(),
              source.transaction().maxPriorityFeePerGasHex(),
              source.transaction().maxFeePerGasHex(),
              source.transaction().expectedNonce()));
    }
    return null;
  }
}
