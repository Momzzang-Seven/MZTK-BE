package momzzangseven.mztkbe.modules.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.application.dto.UpdateClassCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.UpdateClassResult;

/** Use case for updating an existing marketplace class. */
public interface UpdateClassUseCase {

  UpdateClassResult execute(UpdateClassCommand command);
}
