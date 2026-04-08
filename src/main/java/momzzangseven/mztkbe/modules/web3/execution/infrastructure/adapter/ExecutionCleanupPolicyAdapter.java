package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionCleanupPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionCleanupPolicy;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.config.ExecutionEip7702Properties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutionCleanupPolicyAdapter implements LoadExecutionCleanupPolicyPort {

  private final ExecutionEip7702Properties executionEip7702Properties;

  @Override
  public ExecutionCleanupPolicy loadCleanupPolicy() {
    var cleanup = executionEip7702Properties.getCleanup();
    return new ExecutionCleanupPolicy(
        cleanup.getZone(), cleanup.getRetentionDays(), cleanup.getBatchSize());
  }
}
