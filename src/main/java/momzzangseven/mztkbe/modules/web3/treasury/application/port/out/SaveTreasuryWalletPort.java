package momzzangseven.mztkbe.modules.web3.treasury.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;

/**
 * Write-side persistence port for the {@link TreasuryWallet} aggregate. Handles both initial
 * insertion (after {@code provision}) and updates from lifecycle transitions ({@code disable},
 * {@code archive}).
 */
public interface SaveTreasuryWalletPort {

  /**
   * Persist the supplied wallet. Implementations are expected to upsert based on the aggregate's
   * {@code walletAlias} and return a refreshed instance with the database-assigned {@code id} when
   * applicable.
   *
   * @param wallet aggregate to persist; must not be {@code null}
   * @return the persisted aggregate, possibly with a new identifier
   */
  TreasuryWallet save(TreasuryWallet wallet);

  /**
   * Persist a cohort of wallets in a single batch. Used by cohort-wide lifecycle transitions
   * (disable / archive) so every row in a {@code (treasury_address, kms_key_id)} cohort moves to
   * the same status within one transaction. Upsert semantics match {@link #save(TreasuryWallet)}.
   *
   * @param wallets cohort rows to persist; must not be {@code null}
   * @return the persisted aggregates, each possibly refreshed with a database-assigned identifier
   */
  List<TreasuryWallet> saveAll(List<TreasuryWallet> wallets);
}
