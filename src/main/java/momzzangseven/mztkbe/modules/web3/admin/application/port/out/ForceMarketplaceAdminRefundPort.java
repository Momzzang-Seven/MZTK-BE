package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminRefundResult;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarketplaceAdminRefundReason;

public interface ForceMarketplaceAdminRefundPort {

  ForceMarketplaceAdminRefundResult refund(
      Long operatorId,
      Long reservationId,
      MarketplaceAdminRefundReason reasonCode,
      String memo,
      boolean confirmManualRefund,
      boolean canManualRefund);
}
