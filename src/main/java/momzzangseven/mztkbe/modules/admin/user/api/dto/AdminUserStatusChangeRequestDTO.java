package momzzangseven.mztkbe.modules.admin.user.api.dto;

import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.admin.user.application.dto.ChangeAdminUserStatusCommand;

/** Request DTO for {@code PATCH /admin/users/{userId}/status}. */
public record AdminUserStatusChangeRequestDTO(AccountStatus status, String reason) {

  public ChangeAdminUserStatusCommand toCommand(Long operatorUserId, Long targetUserId) {
    String normalizedReason = reason == null || reason.trim().isBlank() ? null : reason.trim();
    ChangeAdminUserStatusCommand command =
        new ChangeAdminUserStatusCommand(operatorUserId, targetUserId, status, normalizedReason);
    command.validate();
    return command;
  }
}
