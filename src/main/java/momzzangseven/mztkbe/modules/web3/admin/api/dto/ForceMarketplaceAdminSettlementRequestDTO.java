package momzzangseven.mztkbe.modules.web3.admin.api.dto;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminSettlementCommand;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminSettlementReason;

public record ForceMarketplaceAdminSettlementRequestDTO(
    MarketplaceAdminSettlementReason reasonCode, String memo, boolean confirmEarlySettle) {

  public ForceMarketplaceAdminSettlementCommand toCommand(Long operatorId, Long reservationId) {
    return new ForceMarketplaceAdminSettlementCommand(
        operatorId, reservationId, reasonCode, memo, confirmEarlySettle);
  }
}
