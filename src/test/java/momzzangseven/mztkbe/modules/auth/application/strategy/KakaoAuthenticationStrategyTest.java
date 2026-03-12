package momzzangseven.mztkbe.modules.auth.application.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.port.in.SocialLoginOutcome;
import momzzangseven.mztkbe.modules.user.application.port.in.SocialLoginUseCase;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KakaoAuthenticationStrategyTest {

  @Mock private KakaoAuthPort kakaoAuthPort;
  @Mock private SocialLoginUseCase socialLoginUseCase;

  @InjectMocks private KakaoAuthenticationStrategy strategy;

  @Test
  void supports_returnsKakao() {
    assertThat(strategy.supports()).isEqualTo(AuthProvider.KAKAO);
  }

  @Test
  void authenticate_returnsExistingUser_whenOutcomeIsExisting() {
    AuthenticationContext context =
        new AuthenticationContext(AuthProvider.KAKAO, null, null, "auth-code", "http://localhost");
    KakaoUserInfo userInfo =
        KakaoUserInfo.builder()
            .providerUserId("kakao-id")
            .email("kakao@example.com")
            .nickname("kakao-user")
            .profileImageUrl("https://img")
            .build();

    when(kakaoAuthPort.getAccessToken("auth-code")).thenReturn("access-token");
    when(kakaoAuthPort.getUserInfo("access-token")).thenReturn(userInfo);
    when(socialLoginUseCase.loginOrRegisterSocial(
            AuthProvider.KAKAO.name(),
            "kakao-id",
            "kakao@example.com",
            "kakao-user",
            "https://img"))
        .thenReturn(SocialLoginOutcome.existing(user()));

    AuthenticatedUser authenticated = strategy.authenticate(context);

    assertThat(authenticated.isNewUser()).isFalse();
    assertThat(authenticated.user().getId()).isEqualTo(2L);
  }

  @Test
  void authenticate_returnsNewUser_whenOutcomeIsNew() {
    AuthenticationContext context =
        new AuthenticationContext(AuthProvider.KAKAO, null, null, "auth-code", "http://localhost");
    KakaoUserInfo userInfo =
        KakaoUserInfo.builder()
            .providerUserId("kakao-id")
            .email("kakao@example.com")
            .nickname("kakao-user")
            .profileImageUrl("https://img")
            .build();

    when(kakaoAuthPort.getAccessToken("auth-code")).thenReturn("access-token");
    when(kakaoAuthPort.getUserInfo("access-token")).thenReturn(userInfo);
    when(socialLoginUseCase.loginOrRegisterSocial(
            AuthProvider.KAKAO.name(),
            "kakao-id",
            "kakao@example.com",
            "kakao-user",
            "https://img"))
        .thenReturn(SocialLoginOutcome.newUser(user()));

    AuthenticatedUser authenticated = strategy.authenticate(context);

    assertThat(authenticated.isNewUser()).isTrue();
  }

  @Test
  void authenticate_throws_whenEmailIsMissing() {
    AuthenticationContext context =
        new AuthenticationContext(AuthProvider.KAKAO, null, null, "auth-code", "http://localhost");
    KakaoUserInfo userInfo =
        KakaoUserInfo.builder()
            .providerUserId("kakao-id")
            .email(null)
            .nickname("kakao-user")
            .profileImageUrl("https://img")
            .build();

    when(kakaoAuthPort.getAccessToken("auth-code")).thenReturn("access-token");
    when(kakaoAuthPort.getUserInfo("access-token")).thenReturn(userInfo);

    assertThatThrownBy(() -> strategy.authenticate(context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Kakao email is required");
  }

  private User user() {
    return User.builder().id(2L).email("kakao@example.com").build();
  }
}
