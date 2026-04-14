package momzzangseven.mztkbe.modules.account.application.dto;

import lombok.Builder;

/**
 * Result object from signup use case.
 *
 * <p>Application Layer DTO that encapsulates signup result data.
 */
@Builder
public record SignupResult(Long userId, String email, String nickname, String role) {

  /** Create SignupResult from individual fields. */
  public static SignupResult of(Long userId, String email, String nickname, String role) {
    return SignupResult.builder().userId(userId).email(email).nickname(nickname).role(role).build();
  }
}
