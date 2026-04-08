package momzzangseven.mztkbe.modules.account.application.port.in;

import momzzangseven.mztkbe.modules.account.application.dto.LogoutCommand;

public interface LogoutUseCase {
  void execute(LogoutCommand command);
}
