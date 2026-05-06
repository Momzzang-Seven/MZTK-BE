package momzzangseven.mztkbe.modules.account.application.dto;

import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;

/** Result for an admin-managed account status change. */
public record UpdateManagedUserAccountStatusResult(Long userId, AccountStatus status) {}
