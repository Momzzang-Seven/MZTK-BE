package momzzangseven.mztkbe.modules.user.application.dto;

import java.time.Instant;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;

/**
 * DTO exposing user profile data to other modules (e.g. account). Fields owned by {@code
 * users_account} (status, deletedAt) are intentionally excluded.
 */
public record UserInfo(
    Long id,
    String email,
    String nickname,
    String profileImageUrl,
    UserRole role,
    Instant createdAt,
    Instant updatedAt) {

  /** Convert from the User domain model. */
  public static UserInfo from(User user) {
    return new UserInfo(
        user.getId(),
        user.getEmail(),
        user.getNickname(),
        user.getProfileImageUrl(),
        user.getRole(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }
}
