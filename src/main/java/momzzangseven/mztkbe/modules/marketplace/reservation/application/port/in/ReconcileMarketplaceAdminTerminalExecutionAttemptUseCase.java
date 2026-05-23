package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReconcileMarketplaceAdminTerminalExecutionAttemptCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReconcileMarketplaceAdminTerminalExecutionAttemptResult;

/** Replays missed terminal hooks for bound marketplace admin execution attempts. */
public interface ReconcileMarketplaceAdminTerminalExecutionAttemptUseCase {

  ReconcileMarketplaceAdminTerminalExecutionAttemptResult execute(
      ReconcileMarketplaceAdminTerminalExecutionAttemptCommand command);
}
