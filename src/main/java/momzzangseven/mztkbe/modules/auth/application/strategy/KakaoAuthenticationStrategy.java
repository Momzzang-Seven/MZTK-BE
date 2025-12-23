package momzzangseven.mztkbe.modules.auth.application.strategy;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.service.UserService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakaoAuthenticationStrategy implements AuthenticationStrategy {

  private final KakaoAuthPort kakaoAuthPort;
  private final UserService userService; // ✅ 추가

  @Override
  public AuthProvider supports() {
    return AuthProvider.KAKAO;
  }

  @Override
  public AuthenticatedUser authenticate(AuthenticationContext context) { // ✅ 반환 타입 변경

    String accessToken = kakaoAuthPort.getAccessToken(context.authorizationCode());
    KakaoUserInfo info = kakaoAuthPort.getUserInfo(accessToken);

    // ✅ (정책) email 필수
    if (info.getEmail() == null || info.getEmail().isBlank()) {
      throw new IllegalArgumentException("Kakao email is required but not provided.");
    }

    // ✅ 기존에 LoginService가 하던 “유저 조회/가입”을 여기서 수행
    UserService.SocialLoginOutcome outcome =
            userService.loginOrRegisterSocial(
                    AuthProvider.KAKAO,
                    info.getProviderUserId(),
                    info.getEmail(),
                    info.getNickname(),
                    info.getProfileImageUrl()
            );

    return outcome.isNewUser()
            ? AuthenticatedUser.newUser(outcome.user())
            : AuthenticatedUser.existing(outcome.user());
  }
}
