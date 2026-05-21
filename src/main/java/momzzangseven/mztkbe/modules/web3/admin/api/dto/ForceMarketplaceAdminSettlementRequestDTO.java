package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminSettleReasonCode;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminSettlementCommand;

public record ForceMarketplaceAdminSettlementRequestDTO(
    MarketplaceAdminSettleReasonCode reasonCode, String memo, boolean confirmEarlySettle) {

  public ForceMarketplaceAdminSettlementCommand toCommand(Long operatorId, Long reservationId) {
    return new ForceMarketplaceAdminSettlementCommand(
        operatorId, reservationId, reasonCode, memo, confirmEarlySettle);
  }
}
