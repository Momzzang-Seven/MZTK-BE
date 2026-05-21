package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.execution;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CreateExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraft;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionIntentIdempotencyMismatchPolicy;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CreateExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionActionTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceStatusCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionResourceTypeCode;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminExecutionDraftSubmitResult;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraft;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionDraftCall;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceUnsignedTxSnapshot;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.SubmitMarketplaceAdminExecutionDraftPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnBean(CreateExecutionIntentUseCase.class)
public class SubmitMarketplaceAdminExecutionIntentAdapter
    implements SubmitMarketplaceAdminExecutionDraftPort {

  private final CreateExecutionIntentUseCase createExecutionIntentUseCase;

  @Override
  public MarketplaceAdminExecutionDraftSubmitResult submit(MarketplaceExecutionDraft draft) {
    CreateExecutionIntentResult result =
        createExecutionIntentUseCase.execute(
            new CreateExecutionIntentCommand(
                toExecutionDraft(draft),
                ExecutionIntentIdempotencyMismatchPolicy.REJECT_ON_MISMATCH));
    return new MarketplaceAdminExecutionDraftSubmitResult(
        result.executionIntentId(),
        result.executionIntentStatus().name(),
        result.mode().name(),
        result.expiresAt(),
        result.existing(),
        result.payloadSnapshotJson());
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
}
