package momzzangseven.mztkbe.modules.account.application.dto;

/** Result of social login find-or-create flow within the account module. */
public record SocialLoginAccountOutcome(AccountUserSnapshot userSnapshot, boolean isNewUser) {

  public static SocialLoginAccountOutcome newUser(AccountUserSnapshot snapshot) {
    return new SocialLoginAccountOutcome(snapshot, true);
  }

  public static SocialLoginAccountOutcome existing(AccountUserSnapshot snapshot) {
    return new SocialLoginAccountOutcome(snapshot, false);
  }
}
