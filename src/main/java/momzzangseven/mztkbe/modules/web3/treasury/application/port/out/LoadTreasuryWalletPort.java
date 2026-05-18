package momzzangseven.mztkbe.modules.web3.treasury.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;

/**
 * Read-side persistence port for the {@link TreasuryWallet} aggregate.
 *
 * <p>Two read variants are exposed:
 *
 * <ul>
 *   <li>{@link #loadByAlias} — lock-free read for read-only paths (state inspection, signability
 *       probe).
 *   <li>{@link #loadByAliasForUpdate} — {@code PESSIMISTIC_WRITE} (SELECT … FOR UPDATE) lock for
 *       write-path callers that must serialize concurrent provision attempts on the same alias.
 *       Returns empty when the row does not exist; the caller relies on the {@code wallet_alias}
 *       UNIQUE constraint to resolve fresh-INSERT races (MOM-444 §4.0.1).
 * </ul>
 */
public interface LoadTreasuryWalletPort {

  /**
   * @param walletAlias canonical alias produced by {@code TreasuryRole#toAlias()}
   * @return the wallet bound to the alias, or empty if no row exists
   */
  Optional<TreasuryWallet> loadByAlias(String walletAlias);

  /**
   * Acquire a {@code PESSIMISTIC_WRITE} (SELECT … FOR UPDATE) lock on the wallet row bound to
   * {@code walletAlias} and return its current snapshot. Used by the provisioning transaction to
   * serialize concurrent operator calls for the same alias (MOM-444). Returns empty when the row
   * does not exist yet — the caller proceeds to INSERT and the {@code wallet_alias} UNIQUE
   * constraint resolves any race between two concurrent fresh-provision calls.
   *
   * <p>MUST be invoked inside a write transaction. Calling it outside of {@code @Transactional} is
   * a programming error (the lock would be released immediately upon connection return).
   */
  Optional<TreasuryWallet> loadByAliasForUpdate(String walletAlias);
}
