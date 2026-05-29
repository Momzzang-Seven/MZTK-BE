package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ListTreasuryWalletsQuery;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;

/**
 * Cross-module entry point for the admin "list every treasury wallet" view.
 *
 * <p>Unlike {@link LoadTreasuryWalletUseCase} this read is <em>not</em> {@code @AdminOnly}-audited:
 * the endpoint is a plain GET that the operator may hit repeatedly while inspecting the keyring, so
 * recording one {@code admin_action_audits} row per call would just dilute the table. Access
 * control is enforced upstream by {@code SecurityConfig} ({@code ROLE_ADMIN} on the matching
 * request mapping); only the mutating provision / disable / archive flows keep audit rows.
 */
public interface ListTreasuryWalletsUseCase {

  /**
   * @param query carries the optional lifecycle status filter already parsed from the raw HTTP
   *     query string by the api layer
   * @return wallets ordered by {@code createdAt} DESC (newest first); never {@code null}
   */
  List<TreasuryWalletView> execute(ListTreasuryWalletsQuery query);
}
