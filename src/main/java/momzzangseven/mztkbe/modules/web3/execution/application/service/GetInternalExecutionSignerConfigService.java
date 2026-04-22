package momzzangseven.mztkbe.modules.web3.execution.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetInternalExecutionSignerConfigUseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionSignerConfigPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionSponsorWalletConfig;

@RequiredArgsConstructor
public class GetInternalExecutionSignerConfigService
    implements GetInternalExecutionSignerConfigUseCase {

  private final LoadInternalExecutionSignerConfigPort loadInternalExecutionSignerConfigPort;

  @Override
  public ExecutionSponsorWalletConfig getSignerConfig() {
    return loadInternalExecutionSignerConfigPort.loadSignerConfig();
  }
}
