package momzzangseven.mztkbe.modules.user.application.dto;

import java.time.Instant;
import lombok.Builder;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;

/** Result of user role update operation. */
@Builder
public record UpdateUserRoleResult(
    Long id,
    String email,
    String name,
    String nickname,
    String profileImageUrl,
    UserRole role,
    Instant createdAt,
    Instant updatedAt) {
  /**
   * Create UpdateUserRoleResult from User domain model.
   *
   * @param user Updated user domain model
   * @return UpdateUserRoleResult containing user information
   */
  public static UpdateUserRoleResult from(User user) {
    return UpdateUserRoleResult.builder()
        .id(user.getId())
        .email(user.getEmail())
        .nickname(user.getNickname())
        .profileImageUrl(user.getProfileImageUrl())
        .role(user.getRole())
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .build();
  }

  /**
   * Validate that the result contains required fields. Called internally after creation to ensure
   * data integrity.
   */
  public void validate() {
    if (id == null || id <= 0) {
      throw new IllegalStateException("User ID must be positive");
    }
    if (email == null || email.isBlank()) {
      throw new IllegalStateException("Email cannot be empty");
    }
    if (role == null) {
      throw new IllegalStateException("Role cannot be null");
    }
  }
}
