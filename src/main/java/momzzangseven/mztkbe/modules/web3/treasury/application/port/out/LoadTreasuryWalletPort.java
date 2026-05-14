package momzzangseven.mztkbe.modules.web3.treasury.application.port.out;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;

/**
 * Read-side persistence port for the {@link TreasuryWallet} aggregate.
 *
 * <p>Used by services that need to fetch a wallet for state inspection (signability checks,
 * disable/archive transitions) or for cohort-aware provisioning. A cohort is the set of wallet rows
 * sharing one {@code (treasury_address, kms_key_id)} pair; implementations translate JPA entities
 * into domain instances, so callers never see the persistence model.
 */
public interface LoadTreasuryWalletPort {

  /**
   * @param walletAlias canonical alias produced by {@code TreasuryRole#toAlias()}
   * @return the wallet bound to the alias, or empty if no row exists
   */
  Optional<TreasuryWallet> loadByAlias(String walletAlias);

  /**
   * Loads every wallet row sharing {@code walletAddress} — i.e. the full cohort for that address.
   * Read-only; use {@link #loadAllByTreasuryAddressForUpdate(String)} when the caller intends to
   * transition the cohort.
   *
   * @param walletAddress {@code 0x}-prefixed treasury address
   * @return all cohort rows, possibly empty; never {@code null}
   */
  List<TreasuryWallet> loadAllByTreasuryAddress(String walletAddress);

  /**
   * Loads the full cohort for {@code walletAddress} with a {@code SELECT ... FOR UPDATE} row lock.
   * Lifecycle services (provision co-bind, disable, archive) call this so concurrent cohort
   * transitions serialize on the same rows. Pair with {@code TreasuryAdvisoryLockPort} which
   * additionally serializes callers that have not yet inserted any cohort row.
   *
   * @param walletAddress {@code 0x}-prefixed treasury address
   * @return all cohort rows under a write lock, possibly empty; never {@code null}
   */
  List<TreasuryWallet> loadAllByTreasuryAddressForUpdate(String walletAddress);
}
