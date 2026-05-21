package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminRefundReasonCode;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminRefundCommand;

public record ForceMarketplaceAdminRefundRequestDTO(
    MarketplaceAdminRefundReasonCode reasonCode, String memo, boolean confirmManualRefund) {

  public ForceMarketplaceAdminRefundCommand toCommand(Long operatorId, Long reservationId) {
    return new ForceMarketplaceAdminRefundCommand(
        operatorId, reservationId, reasonCode, memo, confirmManualRefund);
  }
}
