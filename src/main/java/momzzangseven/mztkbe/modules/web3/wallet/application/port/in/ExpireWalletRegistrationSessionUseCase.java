package momzzangseven.mztkbe.modules.web3.wallet.application.port.in;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ExpireWalletRegistrationSessionCommand;

public interface ExpireWalletRegistrationSessionUseCase {

  boolean execute(ExpireWalletRegistrationSessionCommand command);
}
