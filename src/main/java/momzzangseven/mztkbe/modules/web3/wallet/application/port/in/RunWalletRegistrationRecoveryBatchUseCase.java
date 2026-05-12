package momzzangseven.mztkbe.modules.web3.wallet.application.port.in;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RunWalletRegistrationRecoveryBatchCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RunWalletRegistrationRecoveryBatchResult;

public interface RunWalletRegistrationRecoveryBatchUseCase {

  RunWalletRegistrationRecoveryBatchResult execute(
      RunWalletRegistrationRecoveryBatchCommand command);
}
