package momzzangseven.mztkbe.modules.user.application.port.in;

import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserProfileCommand;
import momzzangseven.mztkbe.modules.user.application.dto.UserInfo;

/** Inbound port for updating a user's profile (nickname, profile image). */
public interface UpdateUserProfileUseCase {

  UserInfo updateProfile(UpdateUserProfileCommand command);
}
