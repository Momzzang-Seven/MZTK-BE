package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Read-only marketplace purchase precheck input before durable reservation hold creation. */
public record PrecheckMarketplacePurchaseCommand(
    Long buyerUserId,
    Long trainerUserId,
    Long classId,
    Long slotId,
    BigInteger signedAmount,
    Integer bookedPriceAmountKrw,
    String buyerWalletAddress,
    String trainerWalletAddress,
    String tokenAddress,
    BigInteger priceBaseUnits) {

  public PrecheckMarketplacePurchaseCommand {
    requirePositive(buyerUserId, "buyerUserId");
    requirePositive(trainerUserId, "trainerUserId");
    requirePositive(classId, "classId");
    requirePositive(slotId, "slotId");
    if (signedAmount == null || signedAmount.signum() <= 0) {
      throw new Web3InvalidInputException("signedAmount must be positive");
    }
    if (bookedPriceAmountKrw == null || bookedPriceAmountKrw <= 0) {
      throw new Web3InvalidInputException("bookedPriceAmountKrw must be positive");
    }
    requireText(buyerWalletAddress, "buyerWalletAddress");
    requireText(trainerWalletAddress, "trainerWalletAddress");
    requireText(tokenAddress, "tokenAddress");
    if (priceBaseUnits == null || priceBaseUnits.signum() <= 0) {
      throw new Web3InvalidInputException("priceBaseUnits must be positive");
    }
  }

  private static void requirePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new Web3InvalidInputException(fieldName + " must be positive");
    }
  }

  private static void requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new Web3InvalidInputException(fieldName + " is required");
    }
  }
}
