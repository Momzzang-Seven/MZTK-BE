package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminEscrowReviewResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionAttemptView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionAuthorityView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionPhase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminParticipantView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReasonReviewOption;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationItem;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminTokenView;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

public record MarketplaceAdminEscrowReviewResponseDTO(
    Long reservationId,
    boolean processable,
    MarketplaceAdminReviewValidationCode baseBlockingCode,
    String blockingReason,
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

  public static MarketplaceAdminEscrowReviewResponseDTO from(
      MarketplaceAdminEscrowReviewResult result) {
    if (result == null) {
      throw new Web3InvalidInputException("result is required");
    }
    return new MarketplaceAdminEscrowReviewResponseDTO(
        result.reservationId(),
        result.processable(),
        result.baseBlockingCode(),
        result.baseBlockingReason(),
        result.reservationStatus(),
        result.escrowStatus(),
        result.buyer(),
        result.trainer(),
        result.token(),
        result.reviewedAt(),
        result.chainCheckedAt(),
        result.reservationVersion(),
        result.adminExecutionPhase(),
        result.nextPollAfterMs(),
        result.pollingEndpoint(),
        result.txHash(),
        result.authority(),
        result.activeExecution(),
        result.lastAttempt(),
        result.baseValidationItems(),
        result.reasonOptions());
  }
}
