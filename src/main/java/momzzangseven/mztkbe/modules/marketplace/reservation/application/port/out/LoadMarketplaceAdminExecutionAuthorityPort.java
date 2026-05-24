package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionAuthorityView;

/** Loads server signer and relayer capability for marketplace admin review preflight. */
public interface LoadMarketplaceAdminExecutionAuthorityPort {

  MarketplaceAdminExecutionAuthorityView load();
}
