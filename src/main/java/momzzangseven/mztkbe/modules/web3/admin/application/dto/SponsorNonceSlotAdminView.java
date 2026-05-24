package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import java.time.LocalDateTime;

public record SponsorNonceSlotAdminView(
    long chainId,
    String fromAddress,
    long nonce,
    String status,
    int attemptNo,
    Long activeAttemptId,
    Long activeTxId,
    String activeTxHash,
    Long consumedAttemptId,
    Long consumedTxId,
    Long consumedExternalEvidenceId,
    Long releasedAttemptId,
    Long releasedTxId,
    LocalDateTime updatedAt) {}
