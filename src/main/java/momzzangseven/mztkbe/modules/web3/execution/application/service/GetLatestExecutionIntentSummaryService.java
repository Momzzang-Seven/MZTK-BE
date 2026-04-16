package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummariesQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetLatestExecutionIntentSummaryUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntent;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class GetLatestExecutionIntentSummaryService
    implements GetLatestExecutionIntentSummaryUseCase {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;
  private final LoadExecutionTransactionPort loadExecutionTransactionPort;

  @Override
  public Optional<GetLatestExecutionIntentSummaryResult> execute(
      GetLatestExecutionIntentSummaryQuery query) {
    return executionIntentPersistencePort
        .findLatestByResource(
            ExecutionResourceType.valueOf(query.resourceType().name()), query.resourceId())
        .map(
            intent ->
                ExecutionIntentViewMapper.toLatestSummary(
                    intent, loadTransaction(intent.getSubmittedTxId())));
  }

  @Override
  public Map<String, GetLatestExecutionIntentSummaryResult> executeBatch(
      GetLatestExecutionIntentSummariesQuery query) {
    Map<String, ExecutionIntent> intentsByResourceId =
        executionIntentPersistencePort.findLatestByResources(
            ExecutionResourceType.valueOf(query.resourceType().name()), query.resourceIds());
    // Transaction summaries are hydrated in one pass so answer-list resume reads do not fan out
    // into per-row execution + transaction lookups.
    Map<Long, ExecutionTransactionSummary> transactionsById =
        loadExecutionTransactionPort.findByIds(
            intentsByResourceId.values().stream()
                .map(ExecutionIntent::getSubmittedTxId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList());

    Map<String, GetLatestExecutionIntentSummaryResult> results = new LinkedHashMap<>();
    intentsByResourceId.forEach(
        (resourceId, intent) ->
            results.put(
                resourceId,
                ExecutionIntentViewMapper.toLatestSummary(
                    intent, loadTransaction(transactionsById, intent.getSubmittedTxId()))));
    return results;
  }

  private Optional<ExecutionTransactionSummary> loadTransaction(Long submittedTxId) {
    if (submittedTxId == null) {
      return Optional.empty();
    }
    return loadExecutionTransactionPort.findById(submittedTxId);
  }

  private Optional<ExecutionTransactionSummary> loadTransaction(
      Map<Long, ExecutionTransactionSummary> transactionsById, Long submittedTxId) {
    if (submittedTxId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(transactionsById.get(submittedTxId));
  }
}
