package momzzangseven.mztkbe.modules.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.application.dto.RejectReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.RejectReservationResult;

/** Input port for a trainer to reject a PENDING reservation. */
public interface RejectReservationUseCase {
  RejectReservationResult execute(RejectReservationCommand command);
}
