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
   * Cross-row collision guard for provisioning: returns {@code true} if a wallet other than the one
   * bound to {@code walletAlias} already owns {@code walletAddress}. The intent is to allow a
   * caller to UPDATE the row matching {@code walletAlias} (backfill mode) without flagging the
   * caller's own row as a conflict, while still detecting genuine address reuse across roles.
   *
   * @param walletAlias canonical alias whose row is being provisioned / backfilled (excluded from
   *     the conflict scan)
   * @param walletAddress {@code 0x}-prefixed Ethereum address recovered from the imported key
   */
  boolean existsAddressOwnedByOther(String walletAlias, String walletAddress);
}
