package momzzangseven.mztkbe.modules.admin.user.application.dto;

import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserAccountStatus;

/** Command for changing a managed user's status from the admin API. */
public record ChangeAdminUserStatusCommand(
    Long operatorUserId, Long targetUserId, AdminUserAccountStatus status, String reason) {

  public void validate() {
    if (operatorUserId == null || operatorUserId <= 0) {
      throw new IllegalArgumentException("operatorUserId must be positive");
    }
    if (targetUserId == null || targetUserId <= 0) {
      throw new IllegalArgumentException("targetUserId must be positive");
    }
    if (status != AdminUserAccountStatus.ACTIVE && status != AdminUserAccountStatus.BLOCKED) {
      throw new IllegalArgumentException("status must be ACTIVE or BLOCKED");
    }
  }
}
