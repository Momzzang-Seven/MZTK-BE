package momzzangseven.mztkbe.modules.admin.user.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import momzzangseven.mztkbe.modules.admin.user.application.dto.ChangeAdminUserStatusCommand;
import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserAccountStatus;

/** Request DTO for {@code PATCH /admin/users/{userId}/status}. */
public record AdminUserStatusChangeRequestDTO(
    @NotNull(message = "Status is required") AdminUserAccountStatus status,
    @Size(max = 500, message = "Reason must not exceed 500 characters") String reason) {

  public ChangeAdminUserStatusCommand toCommand(Long operatorUserId, Long targetUserId) {
    String normalizedReason = reason == null || reason.trim().isBlank() ? null : reason.trim();
    ChangeAdminUserStatusCommand command =
        new ChangeAdminUserStatusCommand(operatorUserId, targetUserId, status, normalizedReason);
    command.validate();
    return command;
  }
}
