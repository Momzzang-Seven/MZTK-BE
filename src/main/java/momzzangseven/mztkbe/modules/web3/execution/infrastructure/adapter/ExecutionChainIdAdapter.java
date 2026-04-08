package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.execution.infrastructure.config.ExecutionCoreProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutionChainIdAdapter implements LoadExecutionChainIdPort {

  private final ExecutionCoreProperties executionCoreProperties;

  @Override
  public long loadChainId() {
    return executionCoreProperties.getChainId();
  }
}
