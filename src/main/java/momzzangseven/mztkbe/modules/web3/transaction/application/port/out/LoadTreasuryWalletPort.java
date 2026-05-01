package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.TreasuryWalletInfo;

/**
 * Transaction-side bridging port for a read-only treasury wallet snapshot. Implemented by an
 * adapter under {@code infrastructure/external/treasury/} that delegates to the treasury module's
 * input port; transaction-layer callers never import treasury types directly.
 */
public interface LoadTreasuryWalletPort {

  /**
   * @param walletAlias canonical alias to look up
   * @return projection of the wallet bound to the alias, or empty if no row exists
   */
  Optional<TreasuryWalletInfo> loadByAlias(String walletAlias, String workerId);
}
