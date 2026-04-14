package momzzangseven.mztkbe.modules.account.application.strategy;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.account.application.dto.GoogleUserInfo;
import momzzangseven.mztkbe.modules.account.application.dto.SocialLoginAccountOutcome;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.service.SocialLoginAccountService;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleAuthenticationStrategy implements AuthenticationStrategy {

  private final GoogleAuthPort googleAuthPort;
  private final SocialLoginAccountService socialLoginAccountService;

  @Override
  public AuthProvider supports() {
    return AuthProvider.GOOGLE;
  }

  @Override
  public AuthenticatedUser authenticate(AuthenticationContext context) {

    String accessToken = googleAuthPort.getAccessToken(context.authorizationCode());
    GoogleUserInfo info = googleAuthPort.getUserInfo(accessToken);

    if (info.getEmail() == null || info.getEmail().isBlank()) {
      throw new IllegalArgumentException("Google email is required but not provided.");
    }

    SocialLoginAccountOutcome outcome =
        socialLoginAccountService.loginOrRegister(
            AuthProvider.GOOGLE,
            info.getProviderUserId(),
            info.getEmail(),
            info.getNickname(),
            info.getProfileImageUrl(),
            context.role());

    return outcome.isNewUser()
        ? AuthenticatedUser.newUser(outcome.userSnapshot())
        : AuthenticatedUser.existing(outcome.userSnapshot());
  }
}
