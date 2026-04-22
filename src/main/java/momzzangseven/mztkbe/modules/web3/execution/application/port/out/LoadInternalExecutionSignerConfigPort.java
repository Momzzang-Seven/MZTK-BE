package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import momzzangseven.mztkbe.modules.web3.execution.domain.vo.ExecutionSponsorWalletConfig;

public interface LoadInternalExecutionSignerConfigPort {

  ExecutionSponsorWalletConfig loadSignerConfig();
}
