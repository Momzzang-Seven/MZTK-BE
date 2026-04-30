package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.DisableTreasuryWalletCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;

/**
 * Operator-invoked transition from {@code ACTIVE} → {@code DISABLED}. Stops new signing flows from
 * accepting the wallet and disables the backing KMS key so that any attempt to use it fails at the
 * HSM boundary.
 */
public interface DisableTreasuryWalletUseCase {

  TreasuryWalletView execute(DisableTreasuryWalletCommand command);
}
