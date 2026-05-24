package momzzangseven.mztkbe.modules.web3.transaction.application.dto.nonce;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.web3.transaction.domain.nonce.SponsorNonceSlotStatus;

@Getter
@Builder
public class RecordSponsorNonceSlotTransitionCommand {

  private final long chainId;
  private final String fromAddress;
  private final long nonce;
  private final SponsorNonceSlotStatus fromStatus;
  private final SponsorNonceSlotStatus toStatus;
  private final Long activeAttemptId;
  private final Long activeTxId;
  private final Long consumedAttemptId;
  private final Long consumedTxId;
  private final Long consumedExternalEvidenceId;
  private final Long releasedAttemptId;
  private final Long releasedTxId;
  private final LocalDateTime stateChangedAt;
  private final String consumedReason;
  private final String releaseReason;
  private final String stuckReason;
  private final String terminalReason;
  private final String replacementClaimOwner;
  private final LocalDateTime replacementClaimExpiresAt;
  private final int replacementPrepareAttemptCount;
  private final String broadcastRecoveryClaimOwner;
  private final String broadcastRecoveryClaimToken;
  private final LocalDateTime broadcastRecoveryClaimExpiresAt;
  private final int broadcastRecoveryAttemptCount;
  private final boolean hasRawTx;
  private final boolean hasTxHash;
  private final boolean hasSigningEvidence;
  private final boolean hasBroadcastEvidence;
  private final boolean hasReceiptEvidence;

  public boolean hasRawTx() {
    return hasRawTx;
  }

  public boolean hasTxHash() {
    return hasTxHash;
  }

  public boolean hasSigningEvidence() {
    return hasSigningEvidence;
  }

  public boolean hasBroadcastEvidence() {
    return hasBroadcastEvidence;
  }

  public boolean hasReceiptEvidence() {
    return hasReceiptEvidence;
  }
}
