package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import java.math.BigInteger;
import java.util.regex.Pattern;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Marketplace-owned sign request bundle exposed to reservation/API response mappers. */
public record MarketplaceSignRequest(
    Authorization authorization, Submit submit, Transaction transaction) {

  private static final Pattern HEX_QUANTITY_PATTERN = Pattern.compile("^0x[0-9a-fA-F]+$");

  public MarketplaceSignRequest {
    boolean hasAuthorizationPath = authorization != null || submit != null;
    boolean hasTransactionPath = transaction != null;

    if (!hasAuthorizationPath && !hasTransactionPath) {
      throw new Web3InvalidInputException("signRequest must include a sign path");
    }
    if (hasAuthorizationPath && (authorization == null || submit == null)) {
      throw new Web3InvalidInputException("authorization and submit must be provided together");
    }
    if (hasAuthorizationPath && hasTransactionPath) {
      throw new Web3InvalidInputException("signRequest cannot mix EIP-7702 and transaction paths");
    }
  }

  public static MarketplaceSignRequest forEip7702(Authorization authorization, Submit submit) {
    return new MarketplaceSignRequest(authorization, submit, null);
  }

  public static MarketplaceSignRequest forEip1559(Transaction transaction) {
    return new MarketplaceSignRequest(null, null, transaction);
  }

  public record Authorization(
      Long chainId, String delegateTarget, Long authorityNonce, String payloadHashToSign) {

    public Authorization {
      if (chainId == null || chainId <= 0) {
        throw new Web3InvalidInputException("authorization.chainId must be positive");
      }
      if (delegateTarget == null || delegateTarget.isBlank()) {
        throw new Web3InvalidInputException("authorization.delegateTarget is required");
      }
      if (authorityNonce == null || authorityNonce < 0) {
        throw new Web3InvalidInputException("authorization.authorityNonce must be >= 0");
      }
      if (payloadHashToSign == null || payloadHashToSign.isBlank()) {
        throw new Web3InvalidInputException("authorization.payloadHashToSign is required");
      }
    }
  }

  public record Submit(String executionDigest, Long deadlineEpochSeconds) {

    public Submit {
      if (executionDigest == null || executionDigest.isBlank()) {
        throw new Web3InvalidInputException("submit.executionDigest is required");
      }
      if (deadlineEpochSeconds == null || deadlineEpochSeconds <= 0) {
        throw new Web3InvalidInputException("submit.deadlineEpochSeconds must be positive");
      }
    }
  }

  public record Transaction(
      Long chainId,
      String fromAddress,
      String toAddress,
      String valueHex,
      String data,
      Long nonce,
      String gasLimitHex,
      String maxPriorityFeePerGasHex,
      String maxFeePerGasHex,
      Long expectedNonce) {

    public Transaction {
      if (chainId == null || chainId <= 0) {
        throw new Web3InvalidInputException("transaction.chainId must be positive");
      }
      if (fromAddress == null || fromAddress.isBlank()) {
        throw new Web3InvalidInputException("transaction.fromAddress is required");
      }
      if (toAddress == null || toAddress.isBlank()) {
        throw new Web3InvalidInputException("transaction.toAddress is required");
      }
      requireNonNegativeHexQuantity(valueHex, "transaction.valueHex");
      if (data == null || data.isBlank()) {
        throw new Web3InvalidInputException("transaction.data is required");
      }
      if (nonce == null || nonce < 0) {
        throw new Web3InvalidInputException("transaction.nonce must be >= 0");
      }
      requirePositiveHexQuantity(gasLimitHex, "transaction.gasLimitHex");
      BigInteger maxPriorityFeePerGas =
          requirePositiveHexQuantity(
              maxPriorityFeePerGasHex, "transaction.maxPriorityFeePerGasHex");
      BigInteger maxFeePerGas =
          requirePositiveHexQuantity(maxFeePerGasHex, "transaction.maxFeePerGasHex");
      if (maxFeePerGas.compareTo(maxPriorityFeePerGas) < 0) {
        throw new Web3InvalidInputException(
            "transaction.maxFeePerGasHex must be >= transaction.maxPriorityFeePerGasHex");
      }
      if (expectedNonce == null || expectedNonce < 0) {
        throw new Web3InvalidInputException("transaction.expectedNonce must be >= 0");
      }
    }

    private static BigInteger requireNonNegativeHexQuantity(String value, String fieldName) {
      BigInteger parsed = parseHexQuantity(value, fieldName);
      if (parsed.signum() < 0) {
        throw new Web3InvalidInputException(fieldName + " must be >= 0");
      }
      return parsed;
    }

    private static BigInteger requirePositiveHexQuantity(String value, String fieldName) {
      BigInteger parsed = parseHexQuantity(value, fieldName);
      if (parsed.signum() <= 0) {
        throw new Web3InvalidInputException(fieldName + " must be positive");
      }
      return parsed;
    }

    private static BigInteger parseHexQuantity(String value, String fieldName) {
      if (value == null || value.isBlank()) {
        throw new Web3InvalidInputException(fieldName + " is required");
      }
      if (!HEX_QUANTITY_PATTERN.matcher(value).matches()) {
        throw new Web3InvalidInputException(fieldName + " must be 0x-prefixed hex");
      }
      return new BigInteger(value.substring(2), 16);
    }
  }
}
