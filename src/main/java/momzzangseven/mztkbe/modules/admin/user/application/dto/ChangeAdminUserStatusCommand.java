package momzzangseven.mztkbe.modules.admin.user.application.dto;

import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;

/** Command for changing a managed user's status from the admin API. */
public record ChangeAdminUserStatusCommand(
    Long operatorUserId, Long targetUserId, AccountStatus status, String reason) {

  public void validate() {
    if (operatorUserId == null || operatorUserId <= 0) {
      throw new IllegalArgumentException("operatorUserId must be positive");
    }
    if (targetUserId == null || targetUserId <= 0) {
      throw new IllegalArgumentException("targetUserId must be positive");
    }
    if (status != AccountStatus.ACTIVE && status != AccountStatus.BLOCKED) {
      throw new IllegalArgumentException("status must be ACTIVE or BLOCKED");
    }
  }
}
