package momzzangseven.mztkbe.modules.web3.wallet.application.port.in;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RetryWalletRegistrationFinalizationCommand;

public interface RetryWalletRegistrationFinalizationUseCase {

  void execute(RetryWalletRegistrationFinalizationCommand command);
}
