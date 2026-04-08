package momzzangseven.mztkbe.modules.web3.transfer.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionActionType;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.transfer.domain.vo.TransferExecutionResourceType;

/** Transfer-owned draft contract submitted to execution through an adapter boundary. */
public record TransferExecutionDraft(
    TransferExecutionResourceType resourceType,
    String resourceId,
    TransferExecutionResourceStatus resourceStatus,
    TransferExecutionActionType actionType,
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
