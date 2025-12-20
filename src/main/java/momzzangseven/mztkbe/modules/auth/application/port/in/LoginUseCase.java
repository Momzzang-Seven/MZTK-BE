package momzzangseven.mztkbe.modules.auth.application.port.in;

import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;

public interface LoginUseCase {
  LoginResult login(AuthenticationContext context);
}
