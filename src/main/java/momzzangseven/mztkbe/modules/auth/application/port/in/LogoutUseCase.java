package momzzangseven.mztkbe.modules.auth.application.port.in;

import momzzangseven.mztkbe.modules.auth.application.dto.LogoutCommand;

public interface LogoutUseCase {
  void execute(LogoutCommand command);
}
