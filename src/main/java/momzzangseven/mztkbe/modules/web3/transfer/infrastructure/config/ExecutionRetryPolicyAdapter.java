package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.config;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionRetryPolicy;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutionRetryPolicyAdapter implements LoadExecutionRetryPolicyPort {

  private final TransferRuntimeConfigAdapter transferRuntimeConfigAdapter;

  @Override
  public ExecutionRetryPolicy loadRetryPolicy() {
    return new ExecutionRetryPolicy(transferRuntimeConfigAdapter.load().retryBackoffSeconds());
  }
}
