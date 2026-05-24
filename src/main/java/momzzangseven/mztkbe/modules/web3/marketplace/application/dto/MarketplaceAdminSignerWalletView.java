package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Marketplace-admin local view of the treasury signer wallet used for server relayer actions. */
public record MarketplaceAdminSignerWalletView(
    String walletAlias, String kmsKeyId, String walletAddress, boolean active) {

  public MarketplaceAdminSignerWalletView {
    if (walletAlias == null || walletAlias.isBlank()) {
      throw new Web3InvalidInputException("walletAlias required");
    }
  }
}
