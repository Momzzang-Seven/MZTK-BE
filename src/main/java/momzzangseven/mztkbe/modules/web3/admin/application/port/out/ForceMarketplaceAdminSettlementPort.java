package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminSettleReasonCode;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminSettlementResult;

public interface ForceMarketplaceAdminSettlementPort {

  ForceMarketplaceAdminSettlementResult settle(
      Long operatorId,
      Long reservationId,
      MarketplaceAdminSettleReasonCode reasonCode,
      String memo,
      boolean confirmEarlySettle,
      boolean canEarlySettle);
}
