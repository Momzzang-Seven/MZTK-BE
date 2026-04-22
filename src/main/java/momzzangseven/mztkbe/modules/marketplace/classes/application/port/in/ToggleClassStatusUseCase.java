package momzzangseven.mztkbe.modules.marketplace.classes.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ToggleClassStatusCommand;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ToggleClassStatusResult;

/** Use case for toggling the active/inactive status of a marketplace class. */
public interface ToggleClassStatusUseCase {

  ToggleClassStatusResult execute(ToggleClassStatusCommand command);
}
