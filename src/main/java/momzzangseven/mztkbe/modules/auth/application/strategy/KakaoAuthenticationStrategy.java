package momzzangseven.mztkbe.modules.auth.application.strategy;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakaoAuthenticationStrategy implements AuthenticationStrategy {

  private final KakaoAuthPort kakaoAuthPort;

  @Override
  public AuthProvider supports() {
    return AuthProvider.KAKAO;
  }

  @Override
  public AuthenticatedUser authenticate(AuthenticationContext context) {
    KakaoUserInfo info = kakaoAuthPort.authenticate(context.getAuthorizationCode());

    return AuthenticatedUser.builder()
        .provider(AuthProvider.KAKAO)
        .providerUserId(info.getProviderUserId())
        .email(info.getEmail())
        .nickname(info.getNickname())
        .profileImageUrl(info.getProfileImageUrl())
        .build();
  }
}
