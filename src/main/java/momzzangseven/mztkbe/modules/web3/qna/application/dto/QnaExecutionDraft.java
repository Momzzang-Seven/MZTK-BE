package momzzangseven.mztkbe.modules.web3.qna.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionResourceType;

public record QnaExecutionDraft(
    QnaExecutionResourceType resourceType,
    String resourceId,
    QnaExecutionResourceStatus resourceStatus,
    QnaExecutionActionType actionType,
    Long requesterUserId,
    Long counterpartyUserId,
    String rootIdempotencyKey,
    String payloadHash,
    String payloadSnapshotJson,
    List<QnaExecutionDraftCall> calls,
    boolean fallbackAllowed,
    String authorityAddress,
    Long authorityNonce,
    String delegateTarget,
    String authorizationPayloadHash,
    QnaUnsignedTxSnapshot unsignedTxSnapshot,
    String unsignedTxFingerprint,
    LocalDateTime expiresAt) {

  public QnaExecutionDraft {
    if (resourceType == null) {
      throw new Web3InvalidInputException("resourceType is required");
    }
    if (resourceId == null || resourceId.isBlank()) {
      throw new Web3InvalidInputException("resourceId is required");
    }
    if (resourceStatus == null) {
      throw new Web3InvalidInputException("resourceStatus is required");
    }
    if (actionType == null) {
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
  }
}
