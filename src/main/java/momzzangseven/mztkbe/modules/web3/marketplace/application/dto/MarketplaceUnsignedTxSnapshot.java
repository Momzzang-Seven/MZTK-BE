package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Marketplace-owned unsigned transaction snapshot for EIP-1559 fallback/internal execution. */
public record MarketplaceUnsignedTxSnapshot(
    long chainId,
    String fromAddress,
    String toAddress,
    BigInteger value,
    String data,
    long nonce,
    BigInteger gasLimit,
    BigInteger maxPriorityFeePerGas,
    BigInteger maxFeePerGas) {

  public MarketplaceUnsignedTxSnapshot {
    if (chainId <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    if (fromAddress == null || fromAddress.isBlank()) {
      throw new Web3InvalidInputException("fromAddress is required");
    }
    if (toAddress == null || toAddress.isBlank()) {
      throw new Web3InvalidInputException("toAddress is required");
    }
    if (value == null || value.signum() < 0) {
      throw new Web3InvalidInputException("value must be >= 0");
    }
    if (data == null || data.isBlank()) {
      throw new Web3InvalidInputException("data is required");
    }
    if (nonce < 0) {
      throw new Web3InvalidInputException("nonce must be >= 0");
    }
    if (gasLimit == null || gasLimit.signum() <= 0) {
      throw new Web3InvalidInputException("gasLimit must be positive");
    }
    if (maxPriorityFeePerGas == null || maxPriorityFeePerGas.signum() < 0) {
      throw new Web3InvalidInputException("maxPriorityFeePerGas must be >= 0");
    }
    if (maxFeePerGas == null || maxFeePerGas.signum() <= 0) {
      throw new Web3InvalidInputException("maxFeePerGas must be positive");
    }
  }
}
