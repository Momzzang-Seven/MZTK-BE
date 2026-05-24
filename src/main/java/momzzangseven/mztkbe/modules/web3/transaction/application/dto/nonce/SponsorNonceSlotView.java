package momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;

public record SponsorNonceSlotView(
    long chainId,
    String fromAddress,
    long nonce,
    SponsorNonceSlotStatus status,
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
