package momzzangseven.mztkbe.modules.account.application.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.account.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.account.application.dto.SocialLoginAccountOutcome;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.account.application.service.SocialLoginAccountService;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KakaoAuthenticationStrategyTest {

  @Mock private KakaoAuthPort kakaoAuthPort;
  @Mock private SocialLoginAccountService socialLoginAccountService;

  @InjectMocks private KakaoAuthenticationStrategy strategy;

  @Test
  void supports_returnsKakao() {
    assertThat(strategy.supports()).isEqualTo(AuthProvider.KAKAO);
  }

  @Test
  void authenticate_returnsExistingUser_whenOutcomeIsExisting() {
    AuthenticationContext context =
        new AuthenticationContext(
            AuthProvider.KAKAO, null, null, "auth-code", "http://localhost", null);
    KakaoUserInfo userInfo =
        KakaoUserInfo.builder()
            .providerUserId("kakao-id")
            .email("kakao@example.com")
            .nickname("kakao-user")
            .profileImageUrl("https://img")
            .build();

    when(kakaoAuthPort.getAccessToken("auth-code")).thenReturn("access-token");
    when(kakaoAuthPort.getUserInfo("access-token")).thenReturn(userInfo);
    when(socialLoginAccountService.loginOrRegister(
            AuthProvider.KAKAO, "kakao-id", "kakao@example.com", "kakao-user", "https://img", null))
        .thenReturn(SocialLoginAccountOutcome.existing(snapshot()));

    AuthenticatedUser authenticated = strategy.authenticate(context);

    assertThat(authenticated.isNewUser()).isFalse();
    assertThat(authenticated.userSnapshot().userId()).isEqualTo(2L);
  }

  @Test
  void authenticate_returnsNewUser_whenOutcomeIsNew() {
    AuthenticationContext context =
        new AuthenticationContext(
            AuthProvider.KAKAO, null, null, "auth-code", "http://localhost", null);
    KakaoUserInfo userInfo =
        KakaoUserInfo.builder()
            .providerUserId("kakao-id")
            .email("kakao@example.com")
            .nickname("kakao-user")
            .profileImageUrl("https://img")
            .build();

    when(kakaoAuthPort.getAccessToken("auth-code")).thenReturn("access-token");
    when(kakaoAuthPort.getUserInfo("access-token")).thenReturn(userInfo);
    when(socialLoginAccountService.loginOrRegister(
            AuthProvider.KAKAO, "kakao-id", "kakao@example.com", "kakao-user", "https://img", null))
        .thenReturn(SocialLoginAccountOutcome.newUser(snapshot()));

    AuthenticatedUser authenticated = strategy.authenticate(context);

    assertThat(authenticated.isNewUser()).isTrue();
  }

  @Test
  void authenticate_throws_whenEmailIsMissing() {
    AuthenticationContext context =
        new AuthenticationContext(
            AuthProvider.KAKAO, null, null, "auth-code", "http://localhost", null);
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

  private AccountUserSnapshot snapshot() {
    return new AccountUserSnapshot(2L, "kakao@example.com", "kakao-user", "https://img", "USER");
  }
}
