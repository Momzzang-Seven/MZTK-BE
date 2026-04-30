package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;

/**
 * Cross-module entry point for read-only {@code TreasuryWallet} lookup. Used by callers that need a
 * snapshot of wallet state (admin UI, signing-flow gates) without exposing the mutable aggregate.
 *
 * <p>The operator id is required because the implementation is admin-audited via
 * {@code @AdminOnly}; every read against this port lands in {@code admin_action_audits} alongside
 * the mutating provision / disable / archive flows.
 */
public interface LoadTreasuryWalletUseCase {

  /**
   * @param walletAlias canonical alias to look up
   * @param operatorUserId admin operator performing the lookup; recorded on the audit row
   * @return view of the wallet bound to the alias, or empty if no row exists
   */
  Optional<TreasuryWalletView> execute(String walletAlias, Long operatorUserId);
}
