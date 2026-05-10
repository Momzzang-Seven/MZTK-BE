package momzzangseven.mztkbe.modules.admin.user.api.dto;

import java.time.Instant;
import momzzangseven.mztkbe.modules.admin.user.application.dto.AdminUserListItemResult;
import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserAccountStatus;
import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserRole;

/** Response row DTO for {@code GET /admin/users}. */
public record AdminUserListItemResponseDTO(
    Long userId,
    String nickname,
    AdminUserRole role,
    String email,
    Instant joinedAt,
    AdminUserAccountStatus status,
    long postCount,
    long commentCount) {

  public static AdminUserListItemResponseDTO from(AdminUserListItemResult result) {
    return new AdminUserListItemResponseDTO(
        result.userId(),
        result.nickname(),
        result.role(),
        result.email(),
        result.joinedAt(),
        result.status(),
        result.postCount(),
        result.commentCount());
  }
}
