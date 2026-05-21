package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

/** Scheduler-only command for marketplace admin settlement execution. */
public record ExecuteMarketplaceSchedulerAdminSettlementCommand(
    Long reservationId, MarketplaceAdminSettleReasonCode reasonCode, String schedulerRunId) {

  public ExecuteMarketplaceSchedulerAdminSettlementCommand {
    schedulerRunId = normalizeSchedulerRunId(schedulerRunId);
  }

  public void validate() {
    requirePositive(reservationId, "reservationId");
    if (reasonCode == null) {
      throw new Web3InvalidInputException("reasonCode is required");
    }
    if (reasonCode == MarketplaceAdminSettleReasonCode.ADMIN_MANUAL_SETTLE) {
      throw new Web3InvalidInputException("scheduler settlement reason must be scheduler-safe");
    }
    if (schedulerRunId == null) {
      throw new Web3InvalidInputException("schedulerRunId is required");
    }
  }

  private static void requirePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new Web3InvalidInputException(fieldName + " must be positive");
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
