package momzzangseven.mztkbe.modules.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.application.dto.ToggleClassStatusCommand;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ToggleClassStatusResult;

/** Use case for toggling the active/inactive status of a marketplace class. */
public interface ToggleClassStatusUseCase {

  ToggleClassStatusResult execute(ToggleClassStatusCommand command);
}
