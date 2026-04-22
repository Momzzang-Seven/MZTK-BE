package momzzangseven.mztkbe.modules.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.application.dto.CompleteReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.CompleteReservationResult;

/** Input port for a user to confirm class completion and trigger settlement. */
public interface CompleteReservationUseCase {
  CompleteReservationResult execute(CompleteReservationCommand command);
}
