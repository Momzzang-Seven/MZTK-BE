package momzzangseven.mztkbe.modules.account.application.strategy;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.account.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.account.application.dto.SocialLoginAccountOutcome;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.account.application.service.SocialLoginAccountService;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakaoAuthenticationStrategy implements AuthenticationStrategy {

  private final KakaoAuthPort kakaoAuthPort;
  private final SocialLoginAccountService socialLoginAccountService;

  @Override
  public AuthProvider supports() {
    return AuthProvider.KAKAO;
  }

  @Override
  public AuthenticatedUser authenticate(AuthenticationContext context) {

    String accessToken = kakaoAuthPort.getAccessToken(context.authorizationCode());
    KakaoUserInfo info = kakaoAuthPort.getUserInfo(accessToken);

    if (info.getEmail() == null || info.getEmail().isBlank()) {
      throw new IllegalArgumentException("Kakao email is required but not provided.");
    }

    SocialLoginAccountOutcome outcome =
        socialLoginAccountService.loginOrRegister(
            AuthProvider.KAKAO,
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
