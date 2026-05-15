package momzzangseven.mztkbe.modules.web3.wallet.application.port.in;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ReconcileWalletRegistrationSessionCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.ReconcileWalletRegistrationSessionResult;

public interface ReconcileWalletRegistrationSessionUseCase {

  ReconcileWalletRegistrationSessionResult execute(
      ReconcileWalletRegistrationSessionCommand command);
}
