package momzzangseven.mztkbe.modules.admin.user.api.dto;

import momzzangseven.mztkbe.modules.admin.user.application.dto.ChangeAdminUserStatusResult;
import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserAccountStatus;

/** Response DTO for an admin user-status change. */
public record AdminUserStatusChangeResponseDTO(Long userId, AdminUserAccountStatus status) {

  public static AdminUserStatusChangeResponseDTO from(ChangeAdminUserStatusResult result) {
    return new AdminUserStatusChangeResponseDTO(result.userId(), result.status());
  }
}
