package momzzangseven.mztkbe.modules.web3.execution.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.execution.domain.vo.UnsignedTxSnapshot;

/** Domain-neutral draft payload used to create a shared execution intent. */
public record ExecutionDraft(
    String resourceType,
    String resourceId,
    String resourceStatus,
    String actionType,
    Long requesterUserId,
    Long counterpartyUserId,
    String rootIdempotencyKey,
    String payloadHash,
    String payloadSnapshotJson,
    List<ExecutionDraftCall> calls,
    boolean fallbackAllowed,
    String authorityAddress,
    Long authorityNonce,
    String delegateTarget,
    String authorizationPayloadHash,
    UnsignedTxSnapshot unsignedTxSnapshot,
    String unsignedTxFingerprint,
    LocalDateTime expiresAt) {

  /** Validates minimal draft requirements for mode selection and sign request generation. */
  public ExecutionDraft {
    if (resourceType == null || resourceType.isBlank()) {
      throw new Web3InvalidInputException("resourceType is required");
    }
    if (resourceId == null || resourceId.isBlank()) {
      throw new Web3InvalidInputException("resourceId is required");
    }
    if (resourceStatus == null || resourceStatus.isBlank()) {
      throw new Web3InvalidInputException("resourceStatus is required");
    }
    if (actionType == null || actionType.isBlank()) {
      throw new Web3InvalidInputException("actionType is required");
    }
    if (requesterUserId == null || requesterUserId <= 0) {
      throw new Web3InvalidInputException("requesterUserId must be positive");
    }
    if (rootIdempotencyKey == null || rootIdempotencyKey.isBlank()) {
      throw new Web3InvalidInputException("rootIdempotencyKey is required");
    }
    if (payloadHash == null || payloadHash.isBlank()) {
      throw new Web3InvalidInputException("payloadHash is required");
    }
    if (calls == null || calls.isEmpty()) {
      throw new Web3InvalidInputException("calls must not be empty");
    }
    if (expiresAt == null) {
      throw new Web3InvalidInputException("expiresAt is required");
    }
    if (authorityAddress == null && unsignedTxSnapshot == null) {
      throw new Web3InvalidInputException(
          "either authorityAddress or unsignedTxSnapshot must be provided");
    }
  }
}
