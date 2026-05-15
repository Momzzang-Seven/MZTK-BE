package momzzangseven.mztkbe.modules.web3.marketplace.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionResourceType;

/** Marketplace-owned draft contract that can later be translated to the shared execution module. */
public record MarketplaceExecutionDraft(
    MarketplaceExecutionResourceType resourceType,
    String resourceId,
    MarketplaceExecutionResourceStatus resourceStatus,
    MarketplaceExecutionActionType actionType,
    Long requesterUserId,
    Long counterpartyUserId,
    String orderId,
    String rootIdempotencyKey,
    String payloadHash,
    String payloadSnapshotJson,
    List<MarketplaceExecutionDraftCall> calls,
    boolean fallbackAllowed,
    String authorityAddress,
    Long authorityNonce,
    String delegateTarget,
    String authorizationPayloadHash,
    MarketplaceUnsignedTxSnapshot unsignedTxSnapshot,
    String unsignedTxFingerprint,
    LocalDateTime expiresAt) {

  public MarketplaceExecutionDraft {
    if (resourceType == null) {
      throw new Web3InvalidInputException("resourceType is required");
    }
    requireText(resourceId, "resourceId");
    if (resourceStatus == null) {
      throw new Web3InvalidInputException("resourceStatus is required");
    }
    if (actionType == null) {
      throw new Web3InvalidInputException("actionType is required");
    }
    requirePositive(requesterUserId, "requesterUserId");
    requireUuid(orderId, "orderId");
    requireText(rootIdempotencyKey, "rootIdempotencyKey");
    requireText(payloadHash, "payloadHash");
    requireText(payloadSnapshotJson, "payloadSnapshotJson");
    if (calls == null || calls.isEmpty()) {
      throw new Web3InvalidInputException("calls must not be empty");
    }
    calls = List.copyOf(calls);
    if (expiresAt == null) {
      throw new Web3InvalidInputException("expiresAt is required");
    }
    validateSigningMaterial(
        fallbackAllowed,
        authorityAddress,
        authorityNonce,
        delegateTarget,
        authorizationPayloadHash,
        unsignedTxSnapshot,
        unsignedTxFingerprint);
  }

  private static void validateSigningMaterial(
      boolean fallbackAllowed,
      String authorityAddress,
      Long authorityNonce,
      String delegateTarget,
      String authorizationPayloadHash,
      MarketplaceUnsignedTxSnapshot unsignedTxSnapshot,
      String unsignedTxFingerprint) {
    boolean hasAuthorityAddress = hasText(authorityAddress);
    boolean hasAuthorityNonce = authorityNonce != null;
    boolean hasDelegateTarget = hasText(delegateTarget);
    boolean hasAuthorizationPayloadHash = hasText(authorizationPayloadHash);
    int authorityFieldCount =
        (hasAuthorityAddress ? 1 : 0)
            + (hasAuthorityNonce ? 1 : 0)
            + (hasDelegateTarget ? 1 : 0)
            + (hasAuthorizationPayloadHash ? 1 : 0);

    if (authorityFieldCount > 0 && authorityFieldCount < 4) {
      throw new Web3InvalidInputException("authority tuple must be all-or-none");
    }
    if (authorityNonce != null && authorityNonce < 0) {
      throw new Web3InvalidInputException("authorityNonce must be >= 0");
    }

    boolean hasAuthorityTuple = authorityFieldCount == 4;
    if (!hasAuthorityTuple && unsignedTxSnapshot == null) {
      throw new Web3InvalidInputException(
          "either authority tuple or unsignedTxSnapshot must be provided");
    }
    if (!hasAuthorityTuple && !fallbackAllowed) {
      throw new Web3InvalidInputException(
          "fallbackAllowed is required when authority tuple is absent");
    }
    if (unsignedTxSnapshot != null && !hasText(unsignedTxFingerprint)) {
      throw new Web3InvalidInputException(
          "unsignedTxFingerprint is required when unsignedTxSnapshot is provided");
    }
    if (unsignedTxSnapshot == null && hasText(unsignedTxFingerprint)) {
      throw new Web3InvalidInputException(
          "unsignedTxSnapshot is required when unsignedTxFingerprint is provided");
    }
  }

  private static void requirePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new Web3InvalidInputException(fieldName + " must be positive");
    }
  }

  private static void requireText(String value, String fieldName) {
    if (!hasText(value)) {
      throw new Web3InvalidInputException(fieldName + " is required");
    }
  }

  private static void requireUuid(String value, String fieldName) {
    requireText(value, fieldName);
    try {
      UUID.fromString(value);
    } catch (IllegalArgumentException ex) {
      throw new Web3InvalidInputException(fieldName + " must be a UUID");
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
