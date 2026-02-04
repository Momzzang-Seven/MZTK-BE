package momzzangseven.mztkbe.modules.location.application.port.in;

import momzzangseven.mztkbe.modules.location.application.dto.RegisterLocationCommand;
import momzzangseven.mztkbe.modules.location.application.dto.RegisterLocationResult;

/** Register Location Usecase Interface */
public interface RegisterLocationUseCase {
  RegisterLocationResult execute(RegisterLocationCommand command);
}
