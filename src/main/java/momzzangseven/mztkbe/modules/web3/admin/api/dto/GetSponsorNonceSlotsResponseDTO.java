package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetSponsorNonceSlotsResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.SponsorNonceSlotAdminView;

public record GetSponsorNonceSlotsResponseDTO(long chainId, String fromAddress, List<Slot> slots) {

  public static GetSponsorNonceSlotsResponseDTO from(GetSponsorNonceSlotsResult result) {
    return new GetSponsorNonceSlotsResponseDTO(
        result.chainId(), result.fromAddress(), result.slots().stream().map(Slot::from).toList());
  }

  public record Slot(
      long nonce,
      String status,
      int attemptNo,
      Long activeAttemptId,
      Long activeTxId,
      String activeTxHash,
      Long consumedAttemptId,
      Long consumedTxId,
      Long consumedExternalEvidenceId,
      LocalDateTime consumedAt,
      String consumedReason,
      Long releasedAttemptId,
      Long releasedTxId,
      LocalDateTime releasedAt,
      String releaseReason,
      String stuckReason,
      String replacementClaimOwner,
      LocalDateTime replacementClaimExpiresAt,
      int replacementPrepareAttemptCount,
      LocalDateTime broadcastStartedAt,
      LocalDateTime lastBroadcastedAt,
      String broadcastRecoveryClaimOwner,
      LocalDateTime broadcastRecoveryClaimExpiresAt,
      int broadcastRecoveryAttemptCount,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {

    private static Slot from(SponsorNonceSlotAdminView view) {
      return new Slot(
          view.nonce(),
          view.status(),
          view.attemptNo(),
          view.activeAttemptId(),
          view.activeTxId(),
          view.activeTxHash(),
          view.consumedAttemptId(),
          view.consumedTxId(),
          view.consumedExternalEvidenceId(),
          view.consumedAt(),
          view.consumedReason(),
          view.releasedAttemptId(),
          view.releasedTxId(),
          view.releasedAt(),
          view.releaseReason(),
          view.stuckReason(),
          view.replacementClaimOwner(),
          view.replacementClaimExpiresAt(),
          view.replacementPrepareAttemptCount(),
          view.broadcastStartedAt(),
          view.lastBroadcastedAt(),
          view.broadcastRecoveryClaimOwner(),
          view.broadcastRecoveryClaimExpiresAt(),
          view.broadcastRecoveryAttemptCount(),
          view.createdAt(),
          view.updatedAt());
    }
  }
}
