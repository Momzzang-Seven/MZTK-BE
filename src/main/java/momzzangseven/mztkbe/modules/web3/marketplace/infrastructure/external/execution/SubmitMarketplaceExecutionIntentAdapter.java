package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.execution;

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
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceSignRequest;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceUnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.SubmitMarketplaceExecutionDraftPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** Submits marketplace drafts to the shared execution module. */
@Component
@RequiredArgsConstructor
@ConditionalOnBean(CreateExecutionIntentUseCase.class)
public class SubmitMarketplaceExecutionIntentAdapter
    implements SubmitMarketplaceExecutionDraftPort {

  private final CreateExecutionIntentUseCase createExecutionIntentUseCase;

  @Override
  public MarketplaceExecutionIntentResult submit(MarketplaceExecutionDraft draft) {
    CreateExecutionIntentResult result =
        createExecutionIntentUseCase.execute(
            new CreateExecutionIntentCommand(toExecutionDraft(draft)));
    return new MarketplaceExecutionIntentResult(
        new MarketplaceExecutionIntentResult.Resource(
            result.resourceType().name(), result.resourceId(), result.resourceStatus().name()),
        draft.actionType().name(),
        draft.orderKey(),
        new MarketplaceExecutionIntentResult.ExecutionIntent(
            result.executionIntentId(),
            result.executionIntentStatus().name(),
            result.expiresAt(),
            result.expiresAtEpochSeconds()),
        new MarketplaceExecutionIntentResult.Execution(result.mode().name(), result.signCount()),
        toSignRequest(result.signRequest()),
        null,
        result.existing(),
        draft.signatureMeta(),
        draft.tokenMovement());
  }

  private ExecutionDraft toExecutionDraft(MarketplaceExecutionDraft draft) {
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

  private List<ExecutionDraftCall> toCalls(List<MarketplaceExecutionDraftCall> calls) {
    return calls.stream()
        .map(call -> new ExecutionDraftCall(call.target(), call.value(), call.data()))
        .toList();
  }

  private UnsignedTxSnapshot toUnsignedTxSnapshot(MarketplaceUnsignedTxSnapshot snapshot) {
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

  private MarketplaceSignRequest toSignRequest(SignRequestBundle request) {
    if (request == null) {
      return null;
    }
    if (request.authorization() != null) {
      return MarketplaceSignRequest.forEip7702(
          new MarketplaceSignRequest.Authorization(
              request.authorization().chainId(),
              request.authorization().delegateTarget(),
              request.authorization().authorityNonce(),
              request.authorization().payloadHashToSign()),
          new MarketplaceSignRequest.Submit(
              request.submit().executionDigest(), request.submit().deadlineEpochSeconds()));
    }
    return MarketplaceSignRequest.forEip1559(
        new MarketplaceSignRequest.Transaction(
            request.transaction().chainId(),
            request.transaction().fromAddress(),
            request.transaction().toAddress(),
            request.transaction().valueHex(),
            request.transaction().data(),
            request.transaction().nonce(),
            request.transaction().gasLimitHex(),
            request.transaction().maxPriorityFeePerGasHex(),
            request.transaction().maxFeePerGasHex(),
            request.transaction().expectedNonce()));
  }
}
