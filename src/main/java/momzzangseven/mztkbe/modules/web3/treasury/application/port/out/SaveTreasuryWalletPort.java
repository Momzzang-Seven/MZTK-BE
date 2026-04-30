package momzzangseven.mztkbe.modules.web3.treasury.application.port.out;

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
}
