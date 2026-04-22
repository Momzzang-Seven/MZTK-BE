package momzzangseven.mztkbe.modules.web3.execution.application.port.in;

import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionSponsorWalletConfig;

public interface GetInternalExecutionSignerConfigUseCase {

  ExecutionSponsorWalletConfig getSignerConfig();
}
