package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record ExecuteMarketplaceAdminRefundCommand(
    Long operatorId,
    Long reservationId,
    MarketplaceAdminRefundReasonCode reasonCode,
    String memo,
    boolean confirmManualRefund,
    boolean canManualRefund) {

  public ExecuteMarketplaceAdminRefundCommand {
    memo = normalizeMemo(memo);
  }

  public void validate() {
    requirePositive(operatorId, "operatorId");
    requirePositive(reservationId, "reservationId");
    if (reasonCode == null) {
      throw new Web3InvalidInputException("reasonCode is required");
    }
    if (reasonCode == MarketplaceAdminRefundReasonCode.ADMIN_MANUAL_REFUND
        && !confirmManualRefund) {
      throw new Web3InvalidInputException("confirmManualRefund is required");
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
