package momzzangseven.mztkbe.modules.web3.wallet.application.port.in;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.FinalizeWalletRegistrationCommand;

public interface FinalizeWalletRegistrationUseCase {

  void execute(FinalizeWalletRegistrationCommand command);
}
