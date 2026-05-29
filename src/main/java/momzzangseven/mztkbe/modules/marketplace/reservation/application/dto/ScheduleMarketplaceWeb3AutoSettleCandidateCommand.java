package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record ScheduleMarketplaceWeb3AutoSettleCandidateCommand(
    MarketplaceWeb3AutoSettleCandidate candidate, String schedulerRunId) {

  public ScheduleMarketplaceWeb3AutoSettleCandidateCommand {
    schedulerRunId = normalizeSchedulerRunId(schedulerRunId);
  }

  public void validate() {
    if (candidate == null) {
      throw new Web3InvalidInputException("candidate is required");
    }
    if (schedulerRunId == null) {
      throw new Web3InvalidInputException("schedulerRunId is required");
    }
  }

  private static String normalizeSchedulerRunId(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.length() > 120) {
      throw new Web3InvalidInputException("schedulerRunId must be 120 characters or less");
    }
    return trimmed;
  }
}
