package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminEscrowReviewView;

public record MarketplaceAdminEscrowReviewResponseDTO(
    Long reservationId,
    boolean processable,
    String baseBlockingCode,
    String blockingReason,
    String reservationStatus,
    String escrowStatus,
    MarketplaceAdminEscrowReviewView.Participant buyer,
    MarketplaceAdminEscrowReviewView.Participant trainer,
    MarketplaceAdminEscrowReviewView.Token token,
    LocalDateTime reviewedAt,
    LocalDateTime chainCheckedAt,
    Long reservationVersion,
    String adminExecutionPhase,
    Long nextPollAfterMs,
    String pollingEndpoint,
    String txHash,
    MarketplaceAdminEscrowReviewView.Authority authority,
    MarketplaceAdminEscrowReviewView.Attempt activeExecution,
    MarketplaceAdminEscrowReviewView.Attempt lastAttempt,
    List<MarketplaceAdminEscrowReviewView.ValidationItem> baseValidationItems,
    List<MarketplaceAdminEscrowReviewView.ReasonOption> reasonOptions) {

  public static MarketplaceAdminEscrowReviewResponseDTO from(
      MarketplaceAdminEscrowReviewView result) {
    if (result == null) {
      throw new Web3InvalidInputException("result is required");
    }
    return new MarketplaceAdminEscrowReviewResponseDTO(
        result.reservationId(),
        result.processable(),
        result.baseBlockingCode(),
        result.blockingReason(),
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
