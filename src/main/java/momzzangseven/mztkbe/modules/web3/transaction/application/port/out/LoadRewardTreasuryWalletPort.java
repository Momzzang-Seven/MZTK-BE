package momzzangseven.mztkbe.modules.web3.transaction.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.TreasuryWalletInfo;

/**
 * Transaction-side bridging port that returns the wallet bound to the reward treasury role.
 *
 * <p>Caller is role-agnostic: the adapter encapsulates the role decision and delegates to the
 * treasury module's input port. A parallel {@code LoadSponsorTreasuryWalletPort} can be added when
 * the EIP-7702 sponsor worker is introduced, without changing existing callers.
 */
public interface LoadRewardTreasuryWalletPort {

  Optional<TreasuryWalletInfo> load();
}
