package momzzangseven.mztkbe.modules.account.application.dto;

import lombok.Builder;

/**
 * Result object from signup use case.
 *
 * <p>Application Layer DTO that encapsulates signup result data.
 */
@Builder
public record SignupResult(Long userId, String email, String nickname) {

  /** Create SignupResult from individual fields. */
  public static SignupResult of(Long userId, String email, String nickname) {
    return SignupResult.builder().userId(userId).email(email).nickname(nickname).build();
  }
}
