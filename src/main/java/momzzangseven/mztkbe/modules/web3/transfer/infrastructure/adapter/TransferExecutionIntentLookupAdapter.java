package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.FindLatestExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.FindLatestExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.transfer.application.port.out.LoadTransferExecutionIntentPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransferExecutionIntentLookupAdapter implements LoadTransferExecutionIntentPort {

  private final FindLatestExecutionIntentUseCase findLatestExecutionIntentUseCase;

  @Override
  public Optional<String> findLatestExecutionIntentId(Long requesterUserId, String resourceId) {
    return findLatestExecutionIntentUseCase
        .execute(
            new FindLatestExecutionIntentQuery(
                requesterUserId, "TRANSFER", resourceId))
        .map(result -> result.executionIntentId());
  }
}
