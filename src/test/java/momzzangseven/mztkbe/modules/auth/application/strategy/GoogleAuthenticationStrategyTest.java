package momzzangseven.mztkbe.modules.auth.application.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.application.dto.GoogleUserInfo;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
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
class GoogleAuthenticationStrategyTest {

  @Mock private GoogleAuthPort googleAuthPort;
  @Mock private SocialLoginUseCase socialLoginUseCase;

  @InjectMocks private GoogleAuthenticationStrategy strategy;

  @Test
  void supports_returnsGoogle() {
    assertThat(strategy.supports()).isEqualTo(AuthProvider.GOOGLE);
  }

  @Test
  void authenticate_returnsExistingUser_whenOutcomeIsExisting() {
    AuthenticationContext context =
        new AuthenticationContext(AuthProvider.GOOGLE, null, null, "auth-code", "http://localhost");
    GoogleUserInfo userInfo =
        GoogleUserInfo.builder()
            .providerUserId("google-sub")
            .email("google@example.com")
            .nickname("google-user")
            .profileImageUrl("https://img")
            .build();

    when(googleAuthPort.getAccessToken("auth-code")).thenReturn("access-token");
    when(googleAuthPort.getUserInfo("access-token")).thenReturn(userInfo);
    when(socialLoginUseCase.loginOrRegisterSocial(
            AuthProvider.GOOGLE.name(),
            "google-sub",
            "google@example.com",
            "google-user",
            "https://img"))
        .thenReturn(SocialLoginOutcome.existing(user()));

    AuthenticatedUser authenticated = strategy.authenticate(context);

    assertThat(authenticated.isNewUser()).isFalse();
    assertThat(authenticated.user().getId()).isEqualTo(1L);
  }

  @Test
  void authenticate_returnsNewUser_whenOutcomeIsNew() {
    AuthenticationContext context =
        new AuthenticationContext(AuthProvider.GOOGLE, null, null, "auth-code", "http://localhost");
    GoogleUserInfo userInfo =
        GoogleUserInfo.builder()
            .providerUserId("google-sub")
            .email("google@example.com")
            .nickname("google-user")
            .profileImageUrl("https://img")
            .build();

    when(googleAuthPort.getAccessToken("auth-code")).thenReturn("access-token");
    when(googleAuthPort.getUserInfo("access-token")).thenReturn(userInfo);
    when(socialLoginUseCase.loginOrRegisterSocial(
            AuthProvider.GOOGLE.name(),
            "google-sub",
            "google@example.com",
            "google-user",
            "https://img"))
        .thenReturn(SocialLoginOutcome.newUser(user()));

    AuthenticatedUser authenticated = strategy.authenticate(context);

    assertThat(authenticated.isNewUser()).isTrue();
  }

  @Test
  void authenticate_throws_whenEmailIsMissing() {
    AuthenticationContext context =
        new AuthenticationContext(AuthProvider.GOOGLE, null, null, "auth-code", "http://localhost");
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

  private User user() {
    return User.builder().id(1L).email("google@example.com").build();
  }
}
