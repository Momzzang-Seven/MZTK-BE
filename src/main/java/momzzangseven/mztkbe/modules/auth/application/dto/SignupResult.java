package momzzangseven.mztkbe.modules.auth.application.dto;

import lombok.Builder;
import momzzangseven.mztkbe.modules.user.domain.model.User;

/**
 * Result object from signup use case.
 *
 * <p>Application Layer DTO that encapsulates signup result data.
 */
@Builder
public record SignupResult(Long userId, String email, String nickname) {
  /**
   * Create SignupResult from User domain model.
   *
   * @param user Created user
   * @return SignupResult
   */
  public static SignupResult from(User user) {
    return SignupResult.builder()
        .userId(user.getId())
        .email(user.getEmail())
        .nickname(user.getNickname())
        .build();
  }
}
