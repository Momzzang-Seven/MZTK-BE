package momzzangseven.mztkbe.modules.auth.application.strategy;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.port.in.SocialLoginOutcome;
import momzzangseven.mztkbe.modules.user.application.port.in.SocialLoginUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakaoAuthenticationStrategy implements AuthenticationStrategy {

  private final KakaoAuthPort kakaoAuthPort;
  private final SocialLoginUseCase socialLoginUseCase;

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

    SocialLoginOutcome outcome =
        socialLoginUseCase.loginOrRegisterSocial(
            AuthProvider.KAKAO.name(),
            info.getProviderUserId(),
            info.getEmail(),
            info.getNickname(),
            info.getProfileImageUrl());

    return outcome.isNewUser()
        ? AuthenticatedUser.newUser(outcome.user())
        : AuthenticatedUser.existing(outcome.user());
  }
}
