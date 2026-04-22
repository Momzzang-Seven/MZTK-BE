package momzzangseven.mztkbe.modules.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.application.dto.CreateReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.CreateReservationResult;

/** Input port for creating a new class reservation. */
public interface CreateReservationUseCase {
  CreateReservationResult execute(CreateReservationCommand command);
}
