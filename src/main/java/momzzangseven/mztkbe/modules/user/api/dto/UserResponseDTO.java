package momzzangseven.mztkbe.modules.user.api.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import momzzangseven.mztkbe.modules.user.application.dto.UpdateUserRoleResult;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;

/** Response DTO for user information. */
@Builder
public record UserResponseDTO(
    Long id,
    String email,
    String name,
    String nickname,
    String bio,
    String profileImageUrl,
    String phoneNumber,
    UserRole role,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  /** Create from UpdateUserRoleResult. Used when converting application layer result DTO. */
  public static UserResponseDTO from(UpdateUserRoleResult result) {
    return UserResponseDTO.builder()
        .id(result.id())
        .email(result.email())
        .name(result.name())
        .nickname(result.nickname())
        .bio(result.bio())
        .profileImageUrl(result.profileImageUrl())
        .phoneNumber(result.phoneNumber())
        .role(result.role())
        .createdAt(result.createdAt())
        .updatedAt(result.updatedAt())
        .build();
  }
}
