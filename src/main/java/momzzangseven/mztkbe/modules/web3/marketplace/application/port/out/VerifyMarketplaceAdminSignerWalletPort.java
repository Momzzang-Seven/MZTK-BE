package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

/** Verifies that the marketplace admin signer wallet is currently usable for signing. */
public interface VerifyMarketplaceAdminSignerWalletPort {

  void verify(String walletAlias);
}
