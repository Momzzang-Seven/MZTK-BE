package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.execution;

import java.util.List;
import java.util.Locale;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Submits marketplace drafts to the shared execution module. */
@Component
@ConditionalOnBean(CreateExecutionIntentUseCase.class)
public class SubmitMarketplaceExecutionIntentAdapter
    implements SubmitMarketplaceExecutionDraftPort {

  private static final String ROOT_ATTEMPT_CONSTRAINT = "uk_web3_execution_intents_root_attempt";

  private final CreateExecutionIntentUseCase createExecutionIntentUseCase;
  private final TransactionTemplate transactionTemplate;

  public SubmitMarketplaceExecutionIntentAdapter(
      CreateExecutionIntentUseCase createExecutionIntentUseCase,
      PlatformTransactionManager transactionManager) {
    this.createExecutionIntentUseCase = createExecutionIntentUseCase;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public MarketplaceExecutionIntentResult submit(MarketplaceExecutionDraft draft) {
    CreateExecutionIntentResult result = submitWithRootRaceRetry(toExecutionDraft(draft));
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

  private CreateExecutionIntentResult submitWithRootRaceRetry(ExecutionDraft draft) {
    CreateExecutionIntentCommand command = new CreateExecutionIntentCommand(draft);
    try {
      return submitOnce(command);
    } catch (DataIntegrityViolationException e) {
      if (!isRootAttemptConstraint(e)) {
        throw e;
      }
      return submitOnce(command);
    }
  }

  private CreateExecutionIntentResult submitOnce(CreateExecutionIntentCommand command) {
    return transactionTemplate.execute(status -> createExecutionIntentUseCase.execute(command));
  }

  private boolean isRootAttemptConstraint(DataIntegrityViolationException e) {
    Throwable current = e;
    while (current != null) {
      String message = current.getMessage();
      if (message != null && message.toLowerCase(Locale.ROOT).contains(ROOT_ATTEMPT_CONSTRAINT)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
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
