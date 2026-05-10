package momzzangseven.mztkbe.modules.admin.user.application.port.out;

import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserAccountStatus;

/** Output port for changing a managed user's account status through the account module. */
public interface ChangeAdminUserAccountStatusPort {

  ChangeAdminUserAccountStatusResult change(Long userId, AdminUserAccountStatus status);

  record ChangeAdminUserAccountStatusResult(Long userId, AdminUserAccountStatus status) {}
}
