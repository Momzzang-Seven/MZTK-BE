package momzzangseven.mztkbe.modules.level.application.port.in;

import momzzangseven.mztkbe.modules.level.application.dto.RunXpGrantReconciliationCommand;
import momzzangseven.mztkbe.modules.level.application.dto.RunXpGrantReconciliationResult;

/** Runs one pass of XP-grant outbox reconciliation, retrying due PENDING rows. */
public interface RunXpGrantReconciliationUseCase {
  RunXpGrantReconciliationResult run(RunXpGrantReconciliationCommand command);
}
