package momzzangseven.mztkbe.modules.web3.execution.domain.vo;

import java.math.BigInteger;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record UnsignedTxSnapshot(
    long chainId,
    String fromAddress,
    String toAddress,
    BigInteger valueWei,
    String data,
    long expectedNonce,
    BigInteger gasLimit,
    BigInteger maxPriorityFeePerGas,
    BigInteger maxFeePerGas) {

  public UnsignedTxSnapshot {
    if (chainId <= 0) {
      throw new Web3InvalidInputException("chainId must be positive");
    }
    if (fromAddress == null || fromAddress.isBlank()) {
      throw new Web3InvalidInputException("fromAddress is required");
    }
    if (toAddress == null || toAddress.isBlank()) {
      throw new Web3InvalidInputException("toAddress is required");
    }
    if (valueWei == null || valueWei.signum() < 0) {
      throw new Web3InvalidInputException("valueWei must be >= 0");
    }
    if (data == null || data.isBlank()) {
      throw new Web3InvalidInputException("data is required");
    }
    if (expectedNonce < 0) {
      throw new Web3InvalidInputException("expectedNonce must be >= 0");
    }
    if (gasLimit == null || gasLimit.signum() <= 0) {
      throw new Web3InvalidInputException("gasLimit must be > 0");
    }
    if (maxPriorityFeePerGas == null || maxPriorityFeePerGas.signum() <= 0) {
      throw new Web3InvalidInputException("maxPriorityFeePerGas must be > 0");
    }
    if (maxFeePerGas == null || maxFeePerGas.signum() <= 0) {
      throw new Web3InvalidInputException("maxFeePerGas must be > 0");
    }
  }
}
