package momzzangseven.mztkbe.modules.account.application.port.in;

import momzzangseven.mztkbe.modules.account.application.dto.LoginCommand;
import momzzangseven.mztkbe.modules.account.application.dto.LoginResult;

/** Input port for login use case. */
public interface LoginUseCase {
  LoginResult execute(LoginCommand command);
}
