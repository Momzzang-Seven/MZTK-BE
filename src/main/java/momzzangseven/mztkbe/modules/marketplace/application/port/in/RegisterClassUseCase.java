package momzzangseven.mztkbe.modules.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.application.dto.RegisterClassCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.RegisterClassResult;

/** Use case for registering a new marketplace class. */
public interface RegisterClassUseCase {

  RegisterClassResult execute(RegisterClassCommand command);
}
