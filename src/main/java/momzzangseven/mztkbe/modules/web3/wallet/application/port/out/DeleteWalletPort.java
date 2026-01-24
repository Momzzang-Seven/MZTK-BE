package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

/** Port for hard deleting user wallet */
public interface DeleteWalletPort {

  /** Delete user wallet in batch */
  Long deleteWalletInBatch(Long walletId);
}
