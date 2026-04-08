package momzzangseven.mztkbe.modules.web3.execution.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.FindLatestExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.FindLatestExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.FindLatestExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FindLatestExecutionIntentService implements FindLatestExecutionIntentUseCase {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;

  @Override
  public Optional<FindLatestExecutionIntentResult> execute(FindLatestExecutionIntentQuery query) {
    return executionIntentPersistencePort
        .findLatestByRequesterAndResource(
            query.requesterUserId(),
            ExecutionResourceType.valueOf(query.resourceType()),
            query.resourceId())
        .map(intent -> new FindLatestExecutionIntentResult(intent.getPublicId()));
  }
}
