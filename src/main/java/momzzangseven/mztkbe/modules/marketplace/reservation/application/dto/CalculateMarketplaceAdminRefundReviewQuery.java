package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record CalculateMarketplaceAdminRefundReviewQuery(
    Long reservationId, boolean canManualRefund) {

  public void validate() {
    if (reservationId == null || reservationId <= 0) {
      throw new Web3InvalidInputException("reservationId must be positive");
    }
  }
}
