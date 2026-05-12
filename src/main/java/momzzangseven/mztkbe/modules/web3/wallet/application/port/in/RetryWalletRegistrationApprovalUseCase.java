package momzzangseven.mztkbe.modules.web3.wallet.application.port.in;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RetryWalletRegistrationApprovalCommand;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationStatusResult;

public interface RetryWalletRegistrationApprovalUseCase {

  WalletRegistrationStatusResult execute(RetryWalletRegistrationApprovalCommand command);
}
