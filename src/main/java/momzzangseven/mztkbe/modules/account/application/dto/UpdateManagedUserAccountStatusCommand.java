package momzzangseven.mztkbe.modules.account.application.dto;

import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;

/** Command for changing a managed user's account status from the admin module. */
public record UpdateManagedUserAccountStatusCommand(Long userId, AccountStatus status) {

  public void validate() {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (status != AccountStatus.ACTIVE && status != AccountStatus.BLOCKED) {
      throw new IllegalArgumentException("status must be ACTIVE or BLOCKED");
    }
  }
}
