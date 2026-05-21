package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminSettleReasonCode;

public record ForceMarketplaceAdminSettlementCommand(
    Long operatorId,
    Long reservationId,
    MarketplaceAdminSettleReasonCode reasonCode,
    String memo,
    boolean confirmEarlySettle) {

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
