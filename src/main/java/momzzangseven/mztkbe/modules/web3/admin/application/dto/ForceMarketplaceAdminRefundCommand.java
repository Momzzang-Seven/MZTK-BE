package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record ForceMarketplaceAdminRefundCommand(
    Long operatorId,
    Long reservationId,
    MarketplaceAdminRefundReason reasonCode,
    String memo,
    boolean confirmManualRefund) {

  public void validate() {
    requirePositive(operatorId, "operatorId");
    requirePositive(reservationId, "reservationId");
    if (reasonCode == null) {
      throw new Web3InvalidInputException("reasonCode is required");
    }
  }

  private static void requirePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new Web3InvalidInputException(fieldName + " must be positive");
    }
  }
}
