package momzzangseven.mztkbe.modules.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.application.dto.ApproveReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ApproveReservationResult;

/** Input port for a trainer to approve a PENDING reservation. */
public interface ApproveReservationUseCase {
  ApproveReservationResult execute(ApproveReservationCommand command);
}
