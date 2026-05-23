package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

import momzzangseven.mztkbe.modules.web3.wallet.domain.model.UserWallet;

/** Port for persisting a wallet and flushing DB constraints inside the current transaction. */
public interface SaveWalletAndFlushPort {

  UserWallet saveAndFlush(UserWallet wallet);
}
