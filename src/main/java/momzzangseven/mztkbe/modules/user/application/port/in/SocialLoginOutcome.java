package momzzangseven.mztkbe.modules.user.application.port.in;

import momzzangseven.mztkbe.modules.user.domain.model.User;

/** Result of social login attempt (existing user or newly created user). */
public record SocialLoginOutcome(User user, boolean isNewUser) {
  public static SocialLoginOutcome newUser(User user) {
    return new SocialLoginOutcome(user, true);
  }

  public static SocialLoginOutcome existing(User user) {
    return new SocialLoginOutcome(user, false);
  }
}
