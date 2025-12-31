package momzzangseven.mztkbe.modules.user.application.dto;

import momzzangseven.mztkbe.global.error.user.IllegalAdminGrantException;
import momzzangseven.mztkbe.global.error.user.InvalidUserRoleException;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;

/**
 * Command for updating user role. Separated from profile update due to different security
 * implications.
 *
 * @param userId user id
 * @param newRole requested role
 */
public record UpdateUserRoleCommand(Long userId, UserRole newRole) {

  /** Validate command fields and business constraints. */
  public void validate() {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("User ID is required");
    }
    if (newRole == null) {
      throw new InvalidUserRoleException("Cannot update user role if no role is provided");
    }
    if (newRole == UserRole.ADMIN) {
      throw new IllegalAdminGrantException();
    }
  }

  /**
   * Static method to create new command from given context.
   *
   * @param userId user id
   * @param newRole requested role
   * @return command
   */
  public static UpdateUserRoleCommand of(Long userId, UserRole newRole) {
    return new UpdateUserRoleCommand(userId, newRole);
  }
}
