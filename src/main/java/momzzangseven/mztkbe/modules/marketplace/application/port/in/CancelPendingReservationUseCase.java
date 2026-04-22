package momzzangseven.mztkbe.modules.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.application.dto.CancelPendingReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.CancelPendingReservationResult;

/** Input port for a user to cancel their own PENDING reservation. */
public interface CancelPendingReservationUseCase {
  CancelPendingReservationResult execute(CancelPendingReservationCommand command);
}
