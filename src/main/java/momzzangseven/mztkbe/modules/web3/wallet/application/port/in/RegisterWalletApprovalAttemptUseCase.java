package momzzangseven.mztkbe.modules.web3.wallet.application.port.in;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RegisterWalletResult;

/** Creates a wallet-registration approval attempt after ownership verification succeeds. */
public interface RegisterWalletApprovalAttemptUseCase {

  RegisterWalletResult createPendingApproval(RegisterWalletCommand command);
}
