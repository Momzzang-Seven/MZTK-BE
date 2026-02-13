package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import momzzangseven.mztkbe.global.error.Web3InvalidInputException;

@Builder
public record PrepareTokenTransferResult(
    String prepareId,
    String idempotencyKey,
    String txType,
    String authorityAddress,
    Long authorityNonce,
    String delegateTarget,
    LocalDateTime authExpiresAt,
    String payloadHashToSign) {

  public PrepareTokenTransferResult {
    validate(
        prepareId,
        idempotencyKey,
        txType,
        authorityAddress,
        authorityNonce,
        delegateTarget,
        authExpiresAt,
        payloadHashToSign);
  }

  private static void validate(
      String prepareId,
      String idempotencyKey,
      String txType,
      String authorityAddress,
      Long authorityNonce,
      String delegateTarget,
      LocalDateTime authExpiresAt,
      String payloadHashToSign) {
    if (prepareId == null || prepareId.isBlank()) {
      throw new Web3InvalidInputException("prepareId is required");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new Web3InvalidInputException("idempotencyKey is required");
    }
    if (txType == null || txType.isBlank()) {
      throw new Web3InvalidInputException("txType is required");
    }
    if (authorityAddress == null || authorityAddress.isBlank()) {
      throw new Web3InvalidInputException("authorityAddress is required");
    }
    if (authorityNonce == null || authorityNonce < 0) {
      throw new Web3InvalidInputException("authorityNonce must be >= 0");
    }
    if (delegateTarget == null || delegateTarget.isBlank()) {
      throw new Web3InvalidInputException("delegateTarget is required");
    }
    if (authExpiresAt == null) {
      throw new Web3InvalidInputException("authExpiresAt is required");
    }
    if (payloadHashToSign == null || payloadHashToSign.isBlank()) {
      throw new Web3InvalidInputException("payloadHashToSign is required");
    }
  }
}
