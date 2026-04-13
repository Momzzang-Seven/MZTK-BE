package momzzangseven.mztkbe.modules.account.application.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.account.application.dto.GoogleUserInfo;
import momzzangseven.mztkbe.modules.account.application.dto.SocialLoginAccountOutcome;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.service.SocialLoginAccountService;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleAuthenticationStrategyTest {

  @Mock private GoogleAuthPort googleAuthPort;
  @Mock private SocialLoginAccountService socialLoginAccountService;

  @InjectMocks private GoogleAuthenticationStrategy strategy;

  @Test
  void supports_returnsGoogle() {
    assertThat(strategy.supports()).isEqualTo(AuthProvider.GOOGLE);
  }

  @Test
  void authenticate_returnsExistingUser_whenOutcomeIsExisting() {
    AuthenticationContext context =
        new AuthenticationContext(
            AuthProvider.GOOGLE, null, null, "auth-code", "http://localhost", null, null);
    GoogleUserInfo userInfo =
        GoogleUserInfo.builder()
            .providerUserId("google-sub")
            .email("google@example.com")
            .nickname("google-user")
            .profileImageUrl("https://img")
            .build();

    when(googleAuthPort.getAccessToken("auth-code")).thenReturn("access-token");
    when(googleAuthPort.getUserInfo("access-token")).thenReturn(userInfo);
    when(socialLoginAccountService.loginOrRegister(
            AuthProvider.GOOGLE,
            "google-sub",
            "google@example.com",
            "google-user",
            "https://img",
            null))
        .thenReturn(SocialLoginAccountOutcome.existing(snapshot()));

    AuthenticatedUser authenticated = strategy.authenticate(context);

    assertThat(authenticated.isNewUser()).isFalse();
    assertThat(authenticated.userSnapshot().userId()).isEqualTo(1L);
  }

  @Test
  void authenticate_returnsNewUser_whenOutcomeIsNew() {
    AuthenticationContext context =
        new AuthenticationContext(
            AuthProvider.GOOGLE, null, null, "auth-code", "http://localhost", null, null);
    GoogleUserInfo userInfo =
        GoogleUserInfo.builder()
            .providerUserId("google-sub")
            .email("google@example.com")
            .nickname("google-user")
            .profileImageUrl("https://img")
            .build();

    when(googleAuthPort.getAccessToken("auth-code")).thenReturn("access-token");
    when(googleAuthPort.getUserInfo("access-token")).thenReturn(userInfo);
    when(socialLoginAccountService.loginOrRegister(
            AuthProvider.GOOGLE,
            "google-sub",
            "google@example.com",
            "google-user",
            "https://img",
            null))
        .thenReturn(SocialLoginAccountOutcome.newUser(snapshot()));

    AuthenticatedUser authenticated = strategy.authenticate(context);

    assertThat(authenticated.isNewUser()).isTrue();
  }

  @Test
  void authenticate_passesTrainerRoleToService_whenContextHasTrainerRole() {
    AuthenticationContext context =
        new AuthenticationContext(
            AuthProvider.GOOGLE, null, null, "auth-code", "http://localhost", "TRAINER", null);
    GoogleUserInfo userInfo =
        GoogleUserInfo.builder()
            .providerUserId("google-sub")
            .email("google@example.com")
            .nickname("google-user")
            .profileImageUrl("https://img")
            .build();

    when(googleAuthPort.getAccessToken("auth-code")).thenReturn("access-token");
    when(googleAuthPort.getUserInfo("access-token")).thenReturn(userInfo);
    when(socialLoginAccountService.loginOrRegister(
            AuthProvider.GOOGLE,
            "google-sub",
            "google@example.com",
            "google-user",
            "https://img",
            "TRAINER"))
        .thenReturn(SocialLoginAccountOutcome.newUser(snapshot()));

    strategy.authenticate(context);

    verify(socialLoginAccountService)
        .loginOrRegister(
            AuthProvider.GOOGLE,
            "google-sub",
            "google@example.com",
            "google-user",
            "https://img",
            "TRAINER");
  }

  @Test
  void authenticate_passesNullRoleToService_whenContextHasNoRole() {
    AuthenticationContext context =
        new AuthenticationContext(
            AuthProvider.GOOGLE, null, null, "auth-code", "http://localhost", null, null);
    GoogleUserInfo userInfo =
        GoogleUserInfo.builder()
            .providerUserId("google-sub")
            .email("google@example.com")
            .nickname("google-user")
            .profileImageUrl("https://img")
            .build();

    when(googleAuthPort.getAccessToken("auth-code")).thenReturn("access-token");
    when(googleAuthPort.getUserInfo("access-token")).thenReturn(userInfo);
    when(socialLoginAccountService.loginOrRegister(
            AuthProvider.GOOGLE,
            "google-sub",
            "google@example.com",
            "google-user",
            "https://img",
            null))
        .thenReturn(SocialLoginAccountOutcome.existing(snapshot()));

    strategy.authenticate(context);

    verify(socialLoginAccountService)
        .loginOrRegister(
            AuthProvider.GOOGLE,
            "google-sub",
            "google@example.com",
            "google-user",
            "https://img",
            null);
  }

  @Test
  void authenticate_throws_whenEmailIsMissing() {
    AuthenticationContext context =
        new AuthenticationContext(
            AuthProvider.GOOGLE, null, null, "auth-code", "http://localhost", null, null);
    GoogleUserInfo userInfo =
        GoogleUserInfo.builder()
            .providerUserId("google-sub")
            .email(" ")
            .nickname("google-user")
            .profileImageUrl("https://img")
            .build();

    when(googleAuthPort.getAccessToken("auth-code")).thenReturn("access-token");
    when(googleAuthPort.getUserInfo("access-token")).thenReturn(userInfo);

    assertThatThrownBy(() -> strategy.authenticate(context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Google email is required");
  }

  private AccountUserSnapshot snapshot() {
    return new AccountUserSnapshot(1L, "google@example.com", "google-user", "https://img", "USER");
  }
}
