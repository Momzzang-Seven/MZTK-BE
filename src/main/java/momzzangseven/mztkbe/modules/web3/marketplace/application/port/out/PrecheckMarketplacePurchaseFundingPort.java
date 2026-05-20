package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

import java.math.BigInteger;

/**
 * Performs read-only token/allowance checks before local marketplace purchase state is retained.
 */
public interface PrecheckMarketplacePurchaseFundingPort {

  void precheck(PurchaseFundingCheck check);

  record PurchaseFundingCheck(
      String buyerWalletAddress,
      String trainerWalletAddress,
      String escrowContractAddress,
      String tokenAddress,
      BigInteger priceBaseUnits) {

    public PurchaseFundingCheck {
      requireAddress(buyerWalletAddress, "buyerWalletAddress");
      requireAddress(trainerWalletAddress, "trainerWalletAddress");
      requireAddress(escrowContractAddress, "escrowContractAddress");
      requireAddress(tokenAddress, "tokenAddress");
      if (priceBaseUnits == null || priceBaseUnits.signum() <= 0) {
        throw new IllegalArgumentException("priceBaseUnits must be positive");
      }
    }

    private static void requireAddress(String value, String fieldName) {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException(fieldName + " is required");
      }
    }
  }
}
