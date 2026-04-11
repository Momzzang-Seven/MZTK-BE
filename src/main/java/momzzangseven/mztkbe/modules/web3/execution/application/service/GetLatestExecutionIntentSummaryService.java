package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionTransactionSummary;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetLatestExecutionIntentSummaryResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetLatestExecutionIntentSummaryUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionTransactionPort;
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

  private Optional<ExecutionTransactionSummary> loadTransaction(Long submittedTxId) {
    if (submittedTxId == null) {
      return Optional.empty();
    }
    return loadExecutionTransactionPort.findById(submittedTxId);
  }
}
