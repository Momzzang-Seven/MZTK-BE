package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;

/**
 * Cross-module entry point for read-only {@code TreasuryWallet} lookup. Used by callers that need a
 * snapshot of wallet state (admin UI, signing-flow gates) without exposing the mutable aggregate.
 */
public interface LoadTreasuryWalletUseCase {

  /**
   * @param walletAlias canonical alias to look up
   * @return view of the wallet bound to the alias, or empty if no row exists
   */
  Optional<TreasuryWalletView> execute(String walletAlias);
}
