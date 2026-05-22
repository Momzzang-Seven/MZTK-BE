package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminRefundCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminRefundReason;

public record ForceMarketplaceAdminRefundRequestDTO(
    MarketplaceAdminRefundReason reasonCode, String memo, boolean confirmManualRefund) {

  public ForceMarketplaceAdminRefundCommand toCommand(Long operatorId, Long reservationId) {
    return new ForceMarketplaceAdminRefundCommand(
        operatorId, reservationId, reasonCode, memo, confirmManualRefund);
  }
}
