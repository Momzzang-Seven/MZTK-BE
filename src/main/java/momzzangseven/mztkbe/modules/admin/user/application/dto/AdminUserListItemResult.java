package momzzangseven.mztkbe.modules.admin.user.application.dto;

import java.time.Instant;
import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserAccountStatus;
import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserRole;

/** Result row for the admin user-management list. */
public record AdminUserListItemResult(
    Long userId,
    String nickname,
    AdminUserRole role,
    String email,
    Instant joinedAt,
    AdminUserAccountStatus status,
    long postCount,
    long commentCount) {}
