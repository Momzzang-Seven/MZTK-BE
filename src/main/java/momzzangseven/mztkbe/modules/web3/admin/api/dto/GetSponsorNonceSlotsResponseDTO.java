package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.GetSponsorNonceSlotsResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.SponsorNonceSlotAdminView;

public record GetSponsorNonceSlotsResponseDTO(
    long chainId, String fromAddress, String serverTimeZone, List<Slot> slots) {

  private static final String RUNBOOK_KEY = "SPONSOR_NONCE_REPLACEMENT";
  private static final String SERVER_TIME_ZONE = "Asia/Seoul";

  public static GetSponsorNonceSlotsResponseDTO from(GetSponsorNonceSlotsResult result) {
    Long lowestBlockingNonce =
        result.slots().stream()
            .filter(slot -> isBlockingStatus(slot.status()))
            .map(SponsorNonceSlotAdminView::nonce)
            .min(Long::compareTo)
            .orElse(null);
    return new GetSponsorNonceSlotsResponseDTO(
        result.chainId(),
        result.fromAddress(),
        SERVER_TIME_ZONE,
        result.slots().stream().map(slot -> Slot.from(slot, lowestBlockingNonce)).toList());
  }

  public record Slot(
      long nonce,
      String status,
      boolean blocking,
      boolean lowestBlockingSlot,
      String severity,
      String displayReason,
      String operatorAction,
      String runbookKey,
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

    private static Slot from(SponsorNonceSlotAdminView view, Long lowestBlockingNonce) {
      return new Slot(
          view.nonce(),
          view.status(),
          isBlockingStatus(view.status()),
          lowestBlockingNonce != null && lowestBlockingNonce == view.nonce(),
          GetSponsorNonceSlotsResponseDTO.severity(view.status()),
          GetSponsorNonceSlotsResponseDTO.displayReason(view),
          GetSponsorNonceSlotsResponseDTO.operatorAction(view.status()),
          GetSponsorNonceSlotsResponseDTO.runbookKey(view.status()),
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

  private static boolean isBlockingStatus(String status) {
    return "OPERATOR_REVIEW_REQUIRED".equals(status);
  }

  private static String displayReason(SponsorNonceSlotAdminView view) {
    if (view.consumedReason() != null && !view.consumedReason().isBlank()) {
      return view.consumedReason();
    }
    if (view.releaseReason() != null && !view.releaseReason().isBlank()) {
      return view.releaseReason();
    }
    if (view.stuckReason() != null && !view.stuckReason().isBlank()) {
      return view.stuckReason();
    }
    return switch (view.status()) {
      case "OPERATOR_REVIEW_REQUIRED" -> "스폰서 발급 재개 전에 운영자 확인이 필요합니다";
      case "CONSUMED_UNKNOWN" -> "체인 nonce가 증가했지만 백엔드 소유 거래를 확인하지 못했습니다";
      case "STUCK" -> "가장 낮은 nonce의 receipt 확인 또는 운영자 복구가 필요할 수 있습니다";
      case "DROPPED" -> "체인에서 확인 가능한 증거가 없어 슬롯이 해제되었습니다";
      default -> null;
    };
  }

  private static String operatorAction(String status) {
    return switch (status) {
      case "OPERATOR_REVIEW_REQUIRED" -> "FOLLOW_SPONSOR_NONCE_REPLACEMENT_RUNBOOK";
      case "CONSUMED_UNKNOWN" -> "REVIEW_BUSINESS_OUTCOME";
      case "STUCK" -> "CHECK_RECEIPT_OR_WAIT_FOR_COORDINATOR";
      case "BROADCASTED" -> "WAIT_FOR_RECEIPT_OR_TIMEOUT";
      case "RESERVED", "SIGNED", "BROADCASTING", "REPLACEMENT_PREPARING" ->
          "MONITOR_IN_FLIGHT_SLOT";
      case "CONSUMED", "DROPPED" -> "NO_ACTION_REQUIRED";
      default -> "CHECK_SLOT_MANUALLY";
    };
  }

  private static String severity(String status) {
    return switch (status) {
      case "OPERATOR_REVIEW_REQUIRED" -> "BLOCKING";
      case "CONSUMED_UNKNOWN", "STUCK", "REPLACEMENT_PREPARING" -> "WARNING";
      default -> "INFO";
    };
  }

  private static String runbookKey(String status) {
    if ("OPERATOR_REVIEW_REQUIRED".equals(status)
        || "CONSUMED_UNKNOWN".equals(status)
        || "STUCK".equals(status)
        || "REPLACEMENT_PREPARING".equals(status)) {
      return RUNBOOK_KEY;
    }
    return null;
  }
}
