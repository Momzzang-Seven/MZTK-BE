package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletRegistrationSession;

/** Save port for wallet registration sessions. */
public interface SaveWalletRegistrationSessionPort {

  WalletRegistrationSession save(WalletRegistrationSession session);
}
