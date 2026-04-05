package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionCleanupPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionCleanupPolicy;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutionCleanupPolicyAdapter implements LoadExecutionCleanupPolicyPort {

  private final TransferRuntimeConfigAdapter transferRuntimeConfigAdapter;

  @Override
  public ExecutionCleanupPolicy loadCleanupPolicy() {
    var config = transferRuntimeConfigAdapter.load();
    return new ExecutionCleanupPolicy(
        config.cleanupZone(), config.cleanupRetentionDays(), config.cleanupBatchSize());
  }
}
