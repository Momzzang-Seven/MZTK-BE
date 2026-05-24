package momzzangseven.mztkbe.modules.web3.execution.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.TreasuryWalletInfo;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;

/**
 * Loads the treasury signer wallet that should issue an internal EIP-1559 execution action.
 *
 * <p>The application layer asks by action type; infrastructure owns the role mapping so marketplace
 * admin actions can use the MARKETPLACE_SIGNER wallet while existing sponsor-backed internal
 * actions keep using SPONSOR.
 */
public interface LoadInternalExecutionSignerWalletPort {

  Optional<TreasuryWalletInfo> load(ExecutionActionType actionType);
}
