package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

/** Loads marketplace contract and token configuration needed before purchase state is retained. */
public interface LoadMarketplacePurchaseConfigPort {

  MarketplacePurchaseConfig loadPurchaseConfig();

  record MarketplacePurchaseConfig(
      String escrowContractAddress, String tokenAddress, int decimals) {

    public MarketplacePurchaseConfig {
      if (escrowContractAddress == null || escrowContractAddress.isBlank()) {
        throw new IllegalArgumentException("escrowContractAddress is required");
      }
      if (tokenAddress == null || tokenAddress.isBlank()) {
        throw new IllegalArgumentException("tokenAddress is required");
      }
      if (decimals < 0) {
        throw new IllegalArgumentException("decimals must be non-negative");
      }
    }
  }
}
