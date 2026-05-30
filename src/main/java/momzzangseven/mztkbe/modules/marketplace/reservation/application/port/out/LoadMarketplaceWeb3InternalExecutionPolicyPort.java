package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3InternalExecutionPolicyStatus;

public interface LoadMarketplaceWeb3InternalExecutionPolicyPort {

  MarketplaceWeb3InternalExecutionPolicyStatus loadInternalExecutionPolicy();
}
