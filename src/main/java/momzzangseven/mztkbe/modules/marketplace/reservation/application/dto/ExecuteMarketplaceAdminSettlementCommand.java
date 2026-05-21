package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record ExecuteMarketplaceAdminSettlementCommand(
    Long operatorId,
    Long reservationId,
    MarketplaceAdminSettleReasonCode reasonCode,
    String memo,
    boolean confirmEarlySettle,
    boolean canEarlySettle) {

  public ExecuteMarketplaceAdminSettlementCommand {
    memo = normalizeMemo(memo);
  }

  public void validate() {
    requirePositive(operatorId, "operatorId");
    requirePositive(reservationId, "reservationId");
    if (reasonCode == null) {
      throw new Web3InvalidInputException("reasonCode is required");
    }
    if (reasonCode == MarketplaceAdminSettleReasonCode.ADMIN_MANUAL_SETTLE && !confirmEarlySettle) {
      throw new Web3InvalidInputException("confirmEarlySettle is required");
    }
  }

  private static void requirePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new Web3InvalidInputException(fieldName + " must be positive");
    }
  }

  private static String normalizeMemo(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.length() > 500) {
      throw new Web3InvalidInputException("memo must be 500 characters or less");
    }
    return trimmed;
  }
}
