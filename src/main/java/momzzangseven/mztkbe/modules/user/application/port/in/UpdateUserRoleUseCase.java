package momzzangseven.mztkbe.modules.user.application.port.in;

import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserRoleCommand;
import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserRoleResult;

/** Usecase for updating user role. */
public interface UpdateUserRoleUseCase {
  /**
   * Update user role (e.g., USER to TRAINER).
   *
   * @param command Update role command
   * @return Update result containing updated user information
   */
  UpdateUserRoleResult execute(UpdateUserRoleCommand command);
}
