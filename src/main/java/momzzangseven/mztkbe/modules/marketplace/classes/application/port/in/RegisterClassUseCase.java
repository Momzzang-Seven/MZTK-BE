package momzzangseven.mztkbe.modules.marketplace.classes.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.RegisterClassCommand;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.RegisterClassResult;

/** Use case for registering a new marketplace class. */
public interface RegisterClassUseCase {

  RegisterClassResult execute(RegisterClassCommand command);
}
