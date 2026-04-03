package momzzangseven.mztkbe.modules.user.application.port.in;

import momzzangseven.mztkbe.modules.user.application.dto.CreateUserCommand;
import momzzangseven.mztkbe.modules.user.application.dto.UserInfo;

/**
 * Inbound port for creating a new user. Called by the account module during signup to create the
 * user profile record.
 */
public interface CreateUserUseCase {

  UserInfo createUser(CreateUserCommand command);
}
