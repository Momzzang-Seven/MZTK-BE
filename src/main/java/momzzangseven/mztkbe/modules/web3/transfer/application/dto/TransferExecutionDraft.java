package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Transfer-owned draft contract submitted to execution through an adapter boundary. */
public record TransferExecutionDraft(
    String resourceType,
    String resourceId,
    String resourceStatus,
    String actionType,
    Long requesterUserId,
    Long counterpartyUserId,
    String rootIdempotencyKey,
    String payloadHash,
    String payloadSnapshotJson,
    List<TransferExecutionDraftCall> calls,
    boolean fallbackAllowed,
    String authorityAddress,
    Long authorityNonce,
    String delegateTarget,
    String authorizationPayloadHash,
    TransferUnsignedTxSnapshot unsignedTxSnapshot,
    String unsignedTxFingerprint,
    LocalDateTime expiresAt) {}
