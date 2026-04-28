package momzzangseven.mztkbe.modules.web3.treasury.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;

/**
 * Read-side persistence port for the {@link TreasuryWallet} aggregate.
 *
 * <p>Used by services that need to fetch a wallet for state inspection (signability checks,
 * disable/archive transitions) or for collision detection during provisioning. Implementations
 * translate JPA entities into domain instances; callers never see the persistence model.
 */
public interface LoadTreasuryWalletPort {

  /**
   * @param walletAlias canonical alias produced by {@code TreasuryRole#toAlias()}
   * @return the wallet bound to the alias, or empty if no row exists
   */
  Optional<TreasuryWallet> loadByAlias(String walletAlias);

  /**
   * Idempotency / collision guard for provisioning: returns {@code true} if any row already binds
   * the supplied alias <em>or</em> wallet address.
   *
   * @param walletAlias canonical alias to check
   * @param walletAddress {@code 0x}-prefixed Ethereum address recovered from the imported key
   */
  boolean existsByAliasOrAddress(String walletAlias, String walletAddress);
}
