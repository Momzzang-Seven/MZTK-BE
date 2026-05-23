package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminSettlementResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminSettlementReason;

public interface ForceMarketplaceAdminSettlementPort {

  ForceMarketplaceAdminSettlementResult settle(
      Long operatorId,
      Long reservationId,
      MarketplaceAdminSettlementReason reasonCode,
      String memo,
      boolean confirmEarlySettle,
      boolean canEarlySettle);
}
