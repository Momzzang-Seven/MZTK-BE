package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminSignerWalletView;

/** Loads the canonical MARKETPLACE_SIGNER wallet for marketplace admin execution. */
public interface LoadMarketplaceAdminSignerWalletPort {

  Optional<MarketplaceAdminSignerWalletView> load();
}
