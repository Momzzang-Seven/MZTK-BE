package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.WalletRegistrationRecoveryStateView;

public interface LoadWalletRegistrationRecoveryStatePort {

  Optional<WalletRegistrationRecoveryStateView> load(String registrationId);
}
