package momzzangseven.mztkbe.modules.web3.wallet.application.port.in;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.GetWalletRegistrationStatusQuery;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationStatusResult;

public interface GetWalletRegistrationStatusUseCase {

  WalletRegistrationStatusResult execute(GetWalletRegistrationStatusQuery query);
}
