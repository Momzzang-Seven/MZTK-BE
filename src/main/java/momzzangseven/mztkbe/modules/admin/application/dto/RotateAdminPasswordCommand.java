package momzzangseven.mztkbe.modules.admin.application.dto;

/**
 * Command for rotating an admin's own password.
 *
 * @param userId the admin's user ID
 * @param currentPassword the current password for verification
 * @param newPassword the new password
 */
public record RotateAdminPasswordCommand(Long userId, String currentPassword, String newPassword) {

  /** Validate command fields. */
  public void validate() {
    if (userId == null) {
      throw new IllegalArgumentException("User ID must not be null");
    }
    if (currentPassword == null || currentPassword.isBlank()) {
      throw new IllegalArgumentException("Current password must not be blank");
    }
    if (newPassword == null || newPassword.isBlank()) {
      throw new IllegalArgumentException("New password must not be blank");
    }
  }
}
