package momzzangseven.mztkbe.modules.web3.execution.domain.vo;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;

public record SignRequestBundle(
    AuthorizationSignRequest authorization,
    SubmitSignRequest submit,
    TransactionSignRequest transaction) {

  public static SignRequestBundle forEip7702(
      AuthorizationSignRequest authorization, SubmitSignRequest submit) {
    if (authorization == null || submit == null) {
      throw new Web3InvalidInputException("authorization and submit are required for EIP7702");
    }
    return new SignRequestBundle(authorization, submit, null);
  }

  public static SignRequestBundle forEip1559(TransactionSignRequest transaction) {
    if (transaction == null) {
      throw new Web3InvalidInputException("transaction is required for EIP1559");
    }
    return new SignRequestBundle(null, null, transaction);
  }

  public void validateForMode(ExecutionMode mode) {
    if (mode == null) {
      throw new Web3InvalidInputException("mode is required");
    }
    if (mode == ExecutionMode.EIP7702 && (authorization == null || submit == null)) {
      throw new Web3InvalidInputException("EIP7702 requires authorization and submit");
    }
    if (mode == ExecutionMode.EIP1559 && transaction == null) {
      throw new Web3InvalidInputException("EIP1559 requires transaction");
    }
  }

  public record AuthorizationSignRequest(
      long chainId, String delegateTarget, long authorityNonce, String payloadHashToSign) {

    public AuthorizationSignRequest {
      if (chainId <= 0) {
        throw new Web3InvalidInputException("chainId must be positive");
      }
      if (delegateTarget == null || delegateTarget.isBlank()) {
        throw new Web3InvalidInputException("delegateTarget is required");
      }
      if (authorityNonce < 0) {
        throw new Web3InvalidInputException("authorityNonce must be >= 0");
      }
      if (payloadHashToSign == null || payloadHashToSign.isBlank()) {
        throw new Web3InvalidInputException("payloadHashToSign is required");
      }
    }
  }

  public record SubmitSignRequest(String executionDigest, long deadlineEpochSeconds) {

    public SubmitSignRequest {
      if (executionDigest == null || executionDigest.isBlank()) {
        throw new Web3InvalidInputException("executionDigest is required");
      }
      if (deadlineEpochSeconds <= 0) {
        throw new Web3InvalidInputException("deadlineEpochSeconds must be positive");
      }
    }
  }

  public record TransactionSignRequest(
      long chainId,
      String fromAddress,
      String toAddress,
      String valueHex,
      String data,
      long nonce,
      String gasLimitHex,
      String maxPriorityFeePerGasHex,
      String maxFeePerGasHex,
      long expectedNonce) {

    public TransactionSignRequest {
      if (chainId <= 0) {
        throw new Web3InvalidInputException("chainId must be positive");
      }
      if (fromAddress == null || fromAddress.isBlank()) {
        throw new Web3InvalidInputException("fromAddress is required");
      }
      if (toAddress == null || toAddress.isBlank()) {
        throw new Web3InvalidInputException("toAddress is required");
      }
      if (valueHex == null || valueHex.isBlank()) {
        throw new Web3InvalidInputException("valueHex is required");
      }
      if (data == null || data.isBlank()) {
        throw new Web3InvalidInputException("data is required");
      }
      if (nonce < 0 || expectedNonce < 0) {
        throw new Web3InvalidInputException("nonce and expectedNonce must be >= 0");
      }
      if (gasLimitHex == null || gasLimitHex.isBlank()) {
        throw new Web3InvalidInputException("gasLimitHex is required");
      }
      if (maxPriorityFeePerGasHex == null || maxPriorityFeePerGasHex.isBlank()) {
        throw new Web3InvalidInputException("maxPriorityFeePerGasHex is required");
      }
      if (maxFeePerGasHex == null || maxFeePerGasHex.isBlank()) {
        throw new Web3InvalidInputException("maxFeePerGasHex is required");
      }
    }
  }
}
