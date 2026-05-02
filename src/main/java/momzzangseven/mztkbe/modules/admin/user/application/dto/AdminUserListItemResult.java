package momzzangseven.mztkbe.modules.admin.user.application.dto;

import java.time.Instant;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;

/** Result row for the admin user-management list. */
public record AdminUserListItemResult(
    Long userId,
    String nickname,
    UserRole role,
    String email,
    Instant joinedAt,
    AccountStatus status,
    long postCount,
    long commentCount) {}
