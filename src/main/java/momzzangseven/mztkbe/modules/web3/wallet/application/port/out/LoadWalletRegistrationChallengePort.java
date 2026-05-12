package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletRegistrationChallengeView;

public interface LoadWalletRegistrationChallengePort {

  Optional<WalletRegistrationChallengeView> load(String nonce);
}
