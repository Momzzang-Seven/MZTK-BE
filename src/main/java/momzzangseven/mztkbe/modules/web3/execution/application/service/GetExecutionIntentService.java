package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.SignRequestBundle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.utils.Numeric;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class GetExecutionIntentService implements GetExecutionIntentUseCase {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final LoadExecutionTransactionPort loadExecutionTransactionPort;
  private final LoadExecutionChainIdPort loadExecutionChainIdPort;

  @Override
  public GetExecutionIntentResult execute(GetExecutionIntentQuery query) {
    ExecutionIntent intent =
        executionIntentPersistencePort
            .findByPublicId(query.executionIntentId())
            .orElseThrow(
                () ->
                    new Web3InvalidInputException(
                        "executionIntentId not found: " + query.executionIntentId()));

    if (!intent.getRequesterUserId().equals(query.requesterUserId())) {
      throw new Web3InvalidInputException("execution intent owner mismatch");
    }

    Optional<ExecutionTransactionSummary> transaction =
        intent.getSubmittedTxId() == null
            ? Optional.empty()
            : loadExecutionTransactionPort.findById(intent.getSubmittedTxId());

    return new GetExecutionIntentResult(
        intent.getResourceType(),
        intent.getResourceId(),
        toResourceStatus(intent.getStatus()),
        intent.getPublicId(),
        intent.getStatus(),
        intent.getExpiresAt(),
        intent.getMode(),
        intent.getMode().requiredSignCount(),
        intent.shouldExposeSignRequest() ? buildSignRequest(intent) : null,
        transaction.map(ExecutionTransactionSummary::transactionId).orElse(null),
        transaction.map(ExecutionTransactionSummary::status).orElse(null),
        transaction.map(ExecutionTransactionSummary::txHash).orElse(null));
  }

  private SignRequestBundle buildSignRequest(ExecutionIntent intent) {
    if (intent.getMode() == ExecutionMode.EIP7702) {
      return SignRequestBundle.forEip7702(
          new SignRequestBundle.AuthorizationSignRequest(
              loadExecutionChainIdPort.loadChainId(),
              intent.getDelegateTarget(),
              intent.getAuthorityNonce(),
              intent.getAuthorizationPayloadHash()),
          new SignRequestBundle.SubmitSignRequest(
              intent.getExecutionDigest(), intent.getExpiresAt().toEpochSecond(ZoneOffset.UTC)));
    }

    if (intent.getUnsignedTxSnapshot() == null) {
      throw new IllegalStateException("EIP1559 intent requires unsignedTxSnapshot");
    }

    return SignRequestBundle.forEip1559(
        new SignRequestBundle.TransactionSignRequest(
            intent.getUnsignedTxSnapshot().chainId(),
            intent.getUnsignedTxSnapshot().fromAddress(),
            intent.getUnsignedTxSnapshot().toAddress(),
            Numeric.encodeQuantity(intent.getUnsignedTxSnapshot().valueWei()),
            intent.getUnsignedTxSnapshot().data(),
            intent.getUnsignedTxSnapshot().expectedNonce(),
            Numeric.encodeQuantity(intent.getUnsignedTxSnapshot().gasLimit()),
            Numeric.encodeQuantity(intent.getUnsignedTxSnapshot().maxPriorityFeePerGas()),
            Numeric.encodeQuantity(intent.getUnsignedTxSnapshot().maxFeePerGas()),
            intent.getUnsignedTxSnapshot().expectedNonce()));
  }

  private ExecutionResourceStatus toResourceStatus(ExecutionIntentStatus status) {
    return switch (status) {
      case AWAITING_SIGNATURE, SIGNED, PENDING_ONCHAIN -> ExecutionResourceStatus.PENDING_EXECUTION;
      case CONFIRMED -> ExecutionResourceStatus.COMPLETED;
      case FAILED_ONCHAIN, EXPIRED, NONCE_STALE, CANCELED -> ExecutionResourceStatus.FAILED;
    };
  }
}
