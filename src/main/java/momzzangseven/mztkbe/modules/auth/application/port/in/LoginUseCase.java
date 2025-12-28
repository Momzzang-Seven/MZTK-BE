package momzzangseven.mztkbe.modules.auth.application.port.in;

import momzzangseven.mztkbe.modules.auth.application.dto.LoginCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;

/** Input port for login use case. */
public interface LoginUseCase {
  LoginResult execute(LoginCommand command);
}
