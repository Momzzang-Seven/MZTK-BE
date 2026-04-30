package momzzangseven.mztkbe.modules.web3.treasury.application.port.in;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ArchiveTreasuryWalletCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;

/**
 * Operator-invoked transition from {@code DISABLED} → {@code ARCHIVED}. Schedules the backing KMS
 * key for permanent deletion (default 30-day pending window) so the wallet can never sign again.
 */
public interface ArchiveTreasuryWalletUseCase {

  TreasuryWalletView execute(ArchiveTreasuryWalletCommand command);
}
