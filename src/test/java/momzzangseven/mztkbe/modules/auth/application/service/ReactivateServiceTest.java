package momzzangseven.mztkbe.modules.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.modules.auth.application.dto.GoogleUserInfo;
import momzzangseven.mztkbe.modules.auth.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.auth.application.dto.ReactivateCommand;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReactivateService unit test")
class ReactivateServiceTest {

  @Mock private LoadUserPort loadUserPort;
  @Mock private SaveUserPort saveUserPort;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private KakaoAuthPort kakaoAuthPort;
  @Mock private GoogleAuthPort googleAuthPort;
  @Mock private AuthTokenIssuer tokenIssuer;

  @InjectMocks private ReactivateService reactivateService;

  @Test
  @DisplayName("LOCAL deleted user is reactivated and token is issued")
  void execute_localDeletedUser_reactivatesAndIssuesToken() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.LOCAL, "user@example.com", "raw-password", null, null);
    User deletedUser =
        User.builder()
            .id(1L)
            .email("user@example.com")
            .password("encoded-password")
            .authProvider(AuthProvider.LOCAL)
            .status(UserStatus.DELETED)
            .role(UserRole.USER)
            .build();

    given(loadUserPort.loadDeletedUserByEmail("user@example.com"))
        .willReturn(Optional.of(deletedUser));
    given(passwordEncoder.matches("raw-password", "encoded-password")).willReturn(true);
    given(saveUserPort.saveUser(any(User.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(tokenIssuer.issue(any(User.class), org.mockito.ArgumentMatchers.eq(false)))
        .willReturn(LoginResult.of("access", "refresh", 10L, 20L, false, deletedUser.reactivate()));

    LoginResult result = reactivateService.execute(command);

    assertThat(result.accessToken()).isEqualTo("access");
    assertThat(result.refreshToken()).isEqualTo("refresh");
    assertThat(result.grantType()).isEqualTo("Bearer");
    assertThat(result.isNewUser()).isFalse();

    verify(loadUserPort).loadDeletedUserByEmail("user@example.com");
    verify(saveUserPort).saveUser(any(User.class));
    verify(tokenIssuer).issue(any(User.class), org.mockito.ArgumentMatchers.eq(false));
    verify(loadUserPort, never()).loadUserByEmail("user@example.com");
  }

  @Test
  @DisplayName("invalid LOCAL command is rejected before any collaborator call")
  void execute_invalidLocalCommand_rejectedBeforeCollaboratorCalls() {
    ReactivateCommand invalid = new ReactivateCommand(AuthProvider.LOCAL, " ", "pw", null, null);

    assertThatThrownBy(() -> reactivateService.execute(invalid))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Email is required for LOCAL reactivation");

    verifyNoInteractions(
        loadUserPort, saveUserPort, passwordEncoder, kakaoAuthPort, googleAuthPort, tokenIssuer);
  }

  @Test
  @DisplayName("LOCAL active user is logged in when deleted user is not found")
  void execute_localActiveUser_logsInAndIssuesToken() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.LOCAL, "active@example.com", "raw-password", null, null);
    User activeUser =
        User.builder()
            .id(2L)
            .email("active@example.com")
            .password("encoded-password")
            .authProvider(AuthProvider.LOCAL)
            .status(UserStatus.ACTIVE)
            .role(UserRole.USER)
            .build();

    given(loadUserPort.loadDeletedUserByEmail("active@example.com")).willReturn(Optional.empty());
    given(loadUserPort.loadUserByEmail("active@example.com")).willReturn(Optional.of(activeUser));
    given(passwordEncoder.matches("raw-password", "encoded-password")).willReturn(true);
    given(saveUserPort.saveUser(any(User.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(tokenIssuer.issue(any(User.class), org.mockito.ArgumentMatchers.eq(false)))
        .willReturn(LoginResult.of("access2", "refresh2", 10L, 20L, false, activeUser));

    LoginResult result = reactivateService.execute(command);

    assertThat(result.accessToken()).isEqualTo("access2");
    verify(loadUserPort).loadUserByEmail("active@example.com");
  }

  @Test
  @DisplayName("LOCAL wrong password throws InvalidCredentialsException")
  void execute_localWrongPassword_throwsInvalidCredentials() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.LOCAL, "user@example.com", "wrong-pw", null, null);
    User deletedUser =
        User.builder()
            .id(1L)
            .email("user@example.com")
            .password("encoded-password")
            .authProvider(AuthProvider.LOCAL)
            .status(UserStatus.DELETED)
            .role(UserRole.USER)
            .build();

    given(loadUserPort.loadDeletedUserByEmail("user@example.com"))
        .willReturn(Optional.of(deletedUser));
    given(passwordEncoder.matches("wrong-pw", "encoded-password")).willReturn(false);

    assertThatThrownBy(() -> reactivateService.execute(command))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(saveUserPort, never()).saveUser(any());
  }

  @Test
  @DisplayName("LOCAL provider mismatch on deleted user throws InvalidCredentialsException")
  void execute_localProviderMismatch_throwsInvalidCredentials() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.LOCAL, "user@example.com", "raw-password", null, null);
    User kakaoDeletedUser =
        User.builder()
            .id(3L)
            .email("user@example.com")
            .authProvider(AuthProvider.KAKAO)
            .status(UserStatus.DELETED)
            .role(UserRole.USER)
            .build();

    given(loadUserPort.loadDeletedUserByEmail("user@example.com"))
        .willReturn(Optional.of(kakaoDeletedUser));

    assertThatThrownBy(() -> reactivateService.execute(command))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(saveUserPort, never()).saveUser(any());
  }

  @Test
  @DisplayName("GOOGLE deleted user is reactivated and token is issued")
  void execute_googleDeletedUser_reactivatesAndIssuesToken() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.GOOGLE, null, null, "google-code", null);
    User deletedUser =
        User.builder()
            .id(4L)
            .authProvider(AuthProvider.GOOGLE)
            .providerUserId("google-provider-1")
            .status(UserStatus.DELETED)
            .role(UserRole.USER)
            .build();

    given(reactivateService_googleAuthPort().getAccessToken("google-code"))
        .willReturn("google-access-token");
    given(reactivateService_googleAuthPort().getUserInfo("google-access-token"))
        .willReturn(
            GoogleUserInfo.builder()
                .providerUserId("google-provider-1")
                .email("google@example.com")
                .build());
    given(loadUserPort.findDeletedByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-provider-1"))
        .willReturn(Optional.of(deletedUser));
    given(saveUserPort.saveUser(any(User.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(tokenIssuer.issue(any(User.class), org.mockito.ArgumentMatchers.eq(false)))
        .willReturn(LoginResult.of("g-access", "g-refresh", 10L, 20L, false, deletedUser.reactivate()));

    LoginResult result = reactivateService.execute(command);

    assertThat(result.accessToken()).isEqualTo("g-access");
    verify(saveUserPort).saveUser(any(User.class));
  }

  @Test
  @DisplayName("social active user is logged in when deleted user is not found")
  void execute_socialActiveUser_logsInAndIssuesToken() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.KAKAO, null, null, "kakao-code", null);
    User activeUser =
        User.builder()
            .id(5L)
            .authProvider(AuthProvider.KAKAO)
            .providerUserId("kakao-provider-2")
            .status(UserStatus.ACTIVE)
            .role(UserRole.USER)
            .build();

    given(kakaoAuthPort.getAccessToken("kakao-code")).willReturn("kakao-token");
    given(kakaoAuthPort.getUserInfo("kakao-token"))
        .willReturn(
            KakaoUserInfo.builder()
                .providerUserId("kakao-provider-2")
                .email("kakao@example.com")
                .build());
    given(loadUserPort.findDeletedByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-provider-2"))
        .willReturn(Optional.empty());
    given(
            loadUserPort.findByProviderAndProviderUserId(
                AuthProvider.KAKAO, "kakao-provider-2"))
        .willReturn(Optional.of(activeUser));
    given(saveUserPort.saveUser(any(User.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(tokenIssuer.issue(any(User.class), org.mockito.ArgumentMatchers.eq(false)))
        .willReturn(LoginResult.of("k-access", "k-refresh", 10L, 20L, false, activeUser));

    LoginResult result = reactivateService.execute(command);

    assertThat(result.accessToken()).isEqualTo("k-access");
    verify(loadUserPort).findByProviderAndProviderUserId(AuthProvider.KAKAO, "kakao-provider-2");
  }

  @Test
  @DisplayName("social user not found throws UserNotFoundException")
  void execute_socialUserNotFound_throwsUserNotFoundException() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.KAKAO, null, null, "kakao-code", null);

    given(kakaoAuthPort.getAccessToken("kakao-code")).willReturn("kakao-token");
    given(kakaoAuthPort.getUserInfo("kakao-token"))
        .willReturn(
            KakaoUserInfo.builder()
                .providerUserId("unknown-id")
                .email("unknown@example.com")
                .build());
    given(loadUserPort.findDeletedByProviderAndProviderUserId(AuthProvider.KAKAO, "unknown-id"))
        .willReturn(Optional.empty());
    given(loadUserPort.findByProviderAndProviderUserId(AuthProvider.KAKAO, "unknown-id"))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> reactivateService.execute(command))
        .isInstanceOf(UserNotFoundException.class);
    verify(saveUserPort, never()).saveUser(any());
  }

  /** Helper to access the googleAuthPort mock without exposing the field directly. */
  private GoogleAuthPort reactivateService_googleAuthPort() {
    return googleAuthPort;
  }

  @Test
  @DisplayName("social provider mismatch on deleted account throws InvalidCredentialsException")
  void execute_socialProviderMismatch_throwsInvalidCredentialsException() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.KAKAO, null, null, "auth-code", null);
    User wrongProviderDeletedUser =
        User.builder()
            .id(2L)
            .authProvider(AuthProvider.GOOGLE)
            .providerUserId("provider-1")
            .status(UserStatus.DELETED)
            .role(UserRole.USER)
            .build();

    given(kakaoAuthPort.getAccessToken("auth-code")).willReturn("kakao-access-token");
    given(kakaoAuthPort.getUserInfo("kakao-access-token"))
        .willReturn(
            KakaoUserInfo.builder()
                .providerUserId("provider-1")
                .email("social@example.com")
                .build());
    given(loadUserPort.findDeletedByProviderAndProviderUserId(AuthProvider.KAKAO, "provider-1"))
        .willReturn(Optional.of(wrongProviderDeletedUser));

    assertThatThrownBy(() -> reactivateService.execute(command))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Invalid email or password");

    verify(saveUserPort, never()).saveUser(any(User.class));
    verifyNoInteractions(tokenIssuer);
  }
}
