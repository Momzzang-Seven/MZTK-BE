package momzzangseven.mztkbe.modules.web3.execution.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadExecutionChainIdPort;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config.Web3CoreProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutionChainIdAdapter implements LoadExecutionChainIdPort {

  private final Web3CoreProperties web3CoreProperties;

  @Override
  public long loadChainId() {
    return web3CoreProperties.getChainId();
  }
}
