package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ResolveExecutionIntentRecoveryTargetQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ResolveExecutionIntentRecoveryTargetResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.ResolveExecutionIntentRecoveryTargetUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResolveExecutionIntentRecoveryTargetService
    implements ResolveExecutionIntentRecoveryTargetUseCase {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final LoadExecutionTransactionPort loadExecutionTransactionPort;

  @Override
  public Optional<ResolveExecutionIntentRecoveryTargetResult> execute(
      ResolveExecutionIntentRecoveryTargetQuery query) {
    if (query == null) {
      throw new Web3InvalidInputException("query is required");
    }

    Optional<ExecutionIntent> intent = resolveIntent(query);
    return intent.map(this::toResult);
  }

  private Optional<ExecutionIntent> resolveIntent(ResolveExecutionIntentRecoveryTargetQuery query) {
    if (query.executionIntentId() != null && !query.executionIntentId().isBlank()) {
      return executionIntentPersistencePort.findByPublicId(query.executionIntentId());
    }
    if (query.transactionId() != null) {
      return executionIntentPersistencePort.findBySubmittedTxId(query.transactionId());
    }
    if (query.resourceType() != null) {
      return executionIntentPersistencePort.findLatestByResource(
          query.resourceType(), query.resourceId());
    }
    throw new Web3InvalidInputException(
        "executionIntentId, transactionId, or resource target is required");
  }

  private ResolveExecutionIntentRecoveryTargetResult toResult(ExecutionIntent intent) {
    Optional<ExecutionTransactionSummary> transaction =
        intent.getSubmittedTxId() == null
            ? Optional.empty()
            : loadExecutionTransactionPort.findById(intent.getSubmittedTxId());
    return new ResolveExecutionIntentRecoveryTargetResult(
        intent.getPublicId(),
        intent.getResourceType(),
        intent.getResourceId(),
        intent.getActionType(),
        intent.getStatus(),
        transaction.map(ExecutionTransactionSummary::transactionId).orElse(null),
        transaction.map(ExecutionTransactionSummary::status).orElse(null),
        transaction.map(ExecutionTransactionSummary::txHash).orElse(null));
  }
}
