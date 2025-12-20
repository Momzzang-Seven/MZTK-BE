package momzzangseven.mztkbe.modules.auth.application.port.in;

import momzzangseven.mztkbe.modules.auth.application.dto.LoginCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;

public interface LoginUseCase {
    public LoginResult execute(LoginCommand command);
}
