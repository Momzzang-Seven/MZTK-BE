package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ApproveReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ApproveReservationResult;

/** Input port for a trainer to approve a PENDING reservation. */
public interface ApproveReservationUseCase {
  ApproveReservationResult execute(ApproveReservationCommand command);
}
