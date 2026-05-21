package momzzangseven.mztkbe.modules.web3.admin.application.port.out;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminRefundReasonCode;
import momzzangseven.mztkbe.modules.web3.admin.application.dto.ForceMarketplaceAdminRefundResult;

public interface ForceMarketplaceAdminRefundPort {

  ForceMarketplaceAdminRefundResult refund(
      Long operatorId,
      Long reservationId,
      MarketplaceAdminRefundReasonCode reasonCode,
      String memo,
      boolean confirmManualRefund,
      boolean canManualRefund);
}
