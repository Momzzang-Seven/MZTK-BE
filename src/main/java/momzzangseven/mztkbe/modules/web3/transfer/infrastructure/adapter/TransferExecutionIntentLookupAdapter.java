package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionIntentPersistencePort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferExecutionIntentPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferExecutionIntentLookupAdapter implements LoadTransferExecutionIntentPort {

  private final ExecutionIntentPersistencePort executionIntentPersistencePort;

  @Override
  public Optional<String> findLatestExecutionIntentId(Long requesterUserId, String resourceId) {
    return executionIntentPersistencePort
        .findLatestByRequesterAndResource(
            requesterUserId, ExecutionResourceType.TRANSFER, resourceId)
        .map(intent -> intent.getPublicId());
  }
}
