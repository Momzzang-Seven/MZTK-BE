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
      Long releasedAttemptId,
      Long releasedTxId,
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
          view.releasedAttemptId(),
          view.releasedTxId(),
          view.updatedAt());
    }
  }
}
