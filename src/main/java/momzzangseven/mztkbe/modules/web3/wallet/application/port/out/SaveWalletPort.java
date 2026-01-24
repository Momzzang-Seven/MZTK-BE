package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;

/** Port for saving and soft deleting user wallet. */
public interface SaveWalletPort {
  /** Save wallet with user id. Soft delete also leverages this method. */
  UserWallet save(UserWallet wallet);
}
