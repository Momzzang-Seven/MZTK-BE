package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

import java.util.Optional;

/** Loads the active wallet used by marketplace user execution prechecks. */
public interface LoadMarketplaceActiveWalletPort {

  Optional<String> loadActiveWalletAddress(Long userId);
}
