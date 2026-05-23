package momzzangseven.mztkbe.modules.web3.admin.application.dto;

import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record GetMarketplaceAdminRefundReviewQuery(Long reservationId) {

  public void validate() {
    if (reservationId == null || reservationId <= 0) {
      throw new Web3InvalidInputException("reservationId must be positive");
    }
  }
}
