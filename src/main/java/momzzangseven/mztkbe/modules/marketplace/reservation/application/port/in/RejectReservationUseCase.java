package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RejectReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RejectReservationResult;

/** Input port for a trainer to reject a PENDING reservation. */
public interface RejectReservationUseCase {
  RejectReservationResult execute(RejectReservationCommand command);
}
