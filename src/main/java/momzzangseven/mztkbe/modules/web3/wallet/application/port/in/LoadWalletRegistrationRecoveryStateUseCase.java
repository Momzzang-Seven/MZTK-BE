package momzzangseven.mztkbe.modules.web3.wallet.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.LoadWalletRegistrationRecoveryStateQuery;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationRecoveryStateResult;

public interface LoadWalletRegistrationRecoveryStateUseCase {

  Optional<WalletRegistrationRecoveryStateResult> execute(
      LoadWalletRegistrationRecoveryStateQuery query);
}
