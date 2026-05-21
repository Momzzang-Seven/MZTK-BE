package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

public record MarketplaceAdminEscrowReviewResult(
    Long reservationId,
    boolean processable,
    MarketplaceAdminReviewValidationCode baseBlockingCode,
    String baseBlockingReason,
    ReservationStatus reservationStatus,
    ReservationEscrowStatus escrowStatus,
    MarketplaceAdminParticipantView buyer,
    MarketplaceAdminParticipantView trainer,
    MarketplaceAdminTokenView token,
    LocalDateTime reviewedAt,
    LocalDateTime chainCheckedAt,
    Long reservationVersion,
    MarketplaceAdminExecutionPhase adminExecutionPhase,
    Long nextPollAfterMs,
    String pollingEndpoint,
    String txHash,
    MarketplaceAdminExecutionAuthorityView authority,
    MarketplaceAdminExecutionAttemptView activeExecution,
    MarketplaceAdminExecutionAttemptView lastAttempt,
    List<MarketplaceAdminReviewValidationItem> baseValidationItems,
    List<MarketplaceAdminReasonReviewOption> reasonOptions) {

  public MarketplaceAdminEscrowReviewResult {
    baseValidationItems =
        baseValidationItems == null ? List.of() : List.copyOf(baseValidationItems);
    reasonOptions = reasonOptions == null ? List.of() : List.copyOf(reasonOptions);
  }
}
