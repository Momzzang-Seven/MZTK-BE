package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/**
 * Marketplace-specific token movement metadata, separate from shared requester/counterparty ids.
 */
public record MarketplaceTokenMovement(
    String tokenAddress,
    BigInteger amountBaseUnits,
    String fromRole,
    String fromAddress,
    String toRole,
    String toAddress) {

  public MarketplaceTokenMovement {
    requireText(tokenAddress, "tokenAddress");
    if (amountBaseUnits == null || amountBaseUnits.signum() < 0) {
      throw new Web3InvalidInputException("amountBaseUnits must be >= 0");
    }
    requireText(fromRole, "fromRole");
    requireText(toRole, "toRole");
  }

  private static void requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new Web3InvalidInputException(fieldName + " is required");
    }
  }
}
