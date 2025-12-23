package momzzangseven.mztkbe.modules.user.application.port.in;

/** Use case for social login (find or register user). */
public interface SocialLoginUseCase {

  SocialLoginOutcome loginOrRegisterSocial(
      String provider,
      String providerUserId,
      String email,
      String nickname,
      String profileImageUrl);
}
