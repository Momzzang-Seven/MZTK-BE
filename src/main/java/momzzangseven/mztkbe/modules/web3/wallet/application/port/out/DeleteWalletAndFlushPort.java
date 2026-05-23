package momzzangseven.mztkbe.modules.web3.wallet.application.port.out;

/** Port for hard deleting a wallet and flushing before inserting the replacement row. */
public interface DeleteWalletAndFlushPort {

  void deleteByIdAndFlush(Long id);
}
