package momzzangseven.mztkbe.modules.admin.user.api.dto;

import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.admin.user.application.dto.ChangeAdminUserStatusResult;

/** Response DTO for an admin user-status change. */
public record AdminUserStatusChangeResponseDTO(Long userId, AccountStatus status) {

  public static AdminUserStatusChangeResponseDTO from(ChangeAdminUserStatusResult result) {
    return new AdminUserStatusChangeResponseDTO(result.userId(), result.status());
  }
}
