package momzzangseven.mztkbe.modules.marketplace.classes.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.UpdateClassCommand;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.UpdateClassResult;

/** Use case for updating an existing marketplace class. */
public interface UpdateClassUseCase {

  UpdateClassResult execute(UpdateClassCommand command);
}
