package momzzangseven.mztkbe.modules.admin.user.application.dto;

import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserAccountStatus;

/** Result for changing a managed user's status from the admin API. */
public record ChangeAdminUserStatusResult(Long userId, AdminUserAccountStatus status) {}
