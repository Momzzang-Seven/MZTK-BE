package momzzangseven.mztkbe.modules.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import momzzangseven.mztkbe.modules.admin.application.dto.RotateAdminPasswordCommand;

/** Request DTO for admin password rotation. */
@Getter
@NoArgsConstructor
public class RotateAdminPasswordRequestDTO {

  @NotBlank(message = "Current password is required")
  private String currentPassword;

  @NotBlank(message = "New password is required")
  private String newPassword;

  /** Convert to application command. */
  public RotateAdminPasswordCommand toCommand(Long userId) {
    return new RotateAdminPasswordCommand(userId, currentPassword, newPassword);
  }
}
