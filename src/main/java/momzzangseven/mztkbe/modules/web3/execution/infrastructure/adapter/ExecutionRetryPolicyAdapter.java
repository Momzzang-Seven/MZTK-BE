package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionRetryPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionRetryPolicy;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.config.ExecutionRewardTokenProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutionRetryPolicyAdapter implements LoadExecutionRetryPolicyPort {

  private final ExecutionRewardTokenProperties executionRewardTokenProperties;

  @Override
  public ExecutionRetryPolicy loadRetryPolicy() {
    return new ExecutionRetryPolicy(
        executionRewardTokenProperties.getWorker().getRetryBackoffSeconds());
  }
}
