package momzzangseven.mztkbe.modules.admin.user.application.port.out;

import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;

/** Output port for changing a managed user's account status through the account module. */
public interface ChangeAdminUserAccountStatusPort {

  ChangeAdminUserAccountStatusResult change(Long userId, AccountStatus status);

  record ChangeAdminUserAccountStatusResult(Long userId, AccountStatus status) {}
}
