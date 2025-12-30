package momzzangseven.mztkbe.modules.user.application.port.in;

import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserRoleCommand;
import momzzangseven.mztkbe.modules.user.domain.model.User;

/** Usecase for updating user role. */
public interface UpdateUserRoleUseCase {
  /**
   * Update user role (e.g., USER to TRAINER).
   *
   * @param command Update role command
   * @return Updated user
   */
  User execute(UpdateUserRoleCommand command);
}
