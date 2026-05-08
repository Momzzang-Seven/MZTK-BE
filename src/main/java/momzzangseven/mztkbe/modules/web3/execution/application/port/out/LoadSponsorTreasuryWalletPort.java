package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;

/**
 * Execution-side bridging port that returns the wallet bound to the sponsor treasury role.
 *
 * <p>Caller is role-agnostic: the adapter encapsulates the {@code SPONSOR} role decision and
 * delegates to the treasury module's input port. Mirrors the transaction-side {@code
 * LoadRewardTreasuryWalletPort}.
 */
public interface LoadSponsorTreasuryWalletPort {

  Optional<TreasuryWalletInfo> load();
}
