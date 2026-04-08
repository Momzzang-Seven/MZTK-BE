package momzzangseven.mztkbe.modules.account.application.service;

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
import momzzangseven.mztkbe.global.error.user.AccountNotDeletedException;
import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.account.application.dto.GoogleUserInfo;
import momzzangseven.mztkbe.modules.account.application.dto.IssuedTokens;
import momzzangseven.mztkbe.modules.account.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.account.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.account.application.dto.ReactivateCommand;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountUserInfoPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserWalletPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
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

  @Mock private LoadUserAccountPort loadUserAccountPort;
  @Mock private SaveUserAccountPort saveUserAccountPort;
  @Mock private LoadAccountUserInfoPort loadAccountUserInfoPort;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private KakaoAuthPort kakaoAuthPort;
  @Mock private GoogleAuthPort googleAuthPort;
  @Mock private AuthTokenIssuer tokenIssuer;
  @Mock private LoadUserWalletPort loadUserWalletPort;

  @InjectMocks private ReactivateService reactivateService;

  private static final IssuedTokens STUB_TOKENS =
      new IssuedTokens("access", "refresh", "Bearer", 10L, 20L);

  @Test
  @DisplayName("LOCAL deleted user is reactivated and token is issued")
  void execute_localDeletedUser_reactivatesAndIssuesToken() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.LOCAL, "user@example.com", "raw-password", null, null);
    UserAccount deletedAccount =
        UserAccount.builder()
            .userId(1L)
            .provider(AuthProvider.LOCAL)
            .passwordHash("encoded-password")
            .status(AccountStatus.DELETED)
            .build();
    AccountUserSnapshot snapshot =
        new AccountUserSnapshot(1L, "user@example.com", "testuser", null, "USER");

    given(loadUserAccountPort.findDeletedByEmail("user@example.com"))
        .willReturn(Optional.of(deletedAccount));
    given(passwordEncoder.matches("raw-password", "encoded-password")).willReturn(true);
    given(saveUserAccountPort.save(any(UserAccount.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(loadAccountUserInfoPort.findById(1L)).willReturn(Optional.of(snapshot));
    given(tokenIssuer.issueTokens(any(), any(), any())).willReturn(STUB_TOKENS);
    given(loadUserWalletPort.loadActiveWalletAddress(1L)).willReturn(Optional.empty());

    LoginResult result = reactivateService.execute(command);

    assertThat(result.accessToken()).isEqualTo("access");
    assertThat(result.refreshToken()).isEqualTo("refresh");
    assertThat(result.grantType()).isEqualTo("Bearer");
    assertThat(result.isNewUser()).isFalse();

    verify(loadUserAccountPort).findDeletedByEmail("user@example.com");
    verify(saveUserAccountPort).save(any(UserAccount.class));
    verify(tokenIssuer).issueTokens(any(), any(), any());
  }

  @Test
  @DisplayName("invalid LOCAL command is rejected before any collaborator call")
  void execute_invalidLocalCommand_rejectedBeforeCollaboratorCalls() {
    ReactivateCommand invalid = new ReactivateCommand(AuthProvider.LOCAL, " ", "pw", null, null);

    assertThatThrownBy(() -> reactivateService.execute(invalid))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Email is required for LOCAL reactivation");

    verifyNoInteractions(
        loadUserAccountPort,
        saveUserAccountPort,
        loadAccountUserInfoPort,
        passwordEncoder,
        kakaoAuthPort,
        googleAuthPort,
        tokenIssuer);
  }

  @Test
  @DisplayName("LOCAL active user throws AccountNotDeletedException when deleted user is not found")
  void execute_localActiveUser_throwsAccountNotDeletedException() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.LOCAL, "active@example.com", "raw-password", null, null);
    UserAccount activeAccount =
        UserAccount.builder()
            .userId(2L)
            .provider(AuthProvider.LOCAL)
            .passwordHash("encoded-password")
            .status(AccountStatus.ACTIVE)
            .build();

    given(loadUserAccountPort.findDeletedByEmail("active@example.com"))
        .willReturn(Optional.empty());
    given(loadUserAccountPort.findActiveByEmail("active@example.com"))
        .willReturn(Optional.of(activeAccount));

    assertThatThrownBy(() -> reactivateService.execute(command))
        .isInstanceOf(AccountNotDeletedException.class);
    verify(saveUserAccountPort, never()).save(any());
  }

  @Test
  @DisplayName("LOCAL wrong password throws InvalidCredentialsException")
  void execute_localWrongPassword_throwsInvalidCredentials() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.LOCAL, "user@example.com", "wrong-pw", null, null);
    UserAccount deletedAccount =
        UserAccount.builder()
            .userId(1L)
            .provider(AuthProvider.LOCAL)
            .passwordHash("encoded-password")
            .status(AccountStatus.DELETED)
            .build();

    given(loadUserAccountPort.findDeletedByEmail("user@example.com"))
        .willReturn(Optional.of(deletedAccount));
    given(passwordEncoder.matches("wrong-pw", "encoded-password")).willReturn(false);

    assertThatThrownBy(() -> reactivateService.execute(command))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(saveUserAccountPort, never()).save(any());
  }

  @Test
  @DisplayName("LOCAL provider mismatch on deleted user throws InvalidCredentialsException")
  void execute_localProviderMismatch_throwsInvalidCredentials() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.LOCAL, "user@example.com", "raw-password", null, null);
    UserAccount kakaoDeletedAccount =
        UserAccount.builder()
            .userId(3L)
            .provider(AuthProvider.KAKAO)
            .status(AccountStatus.DELETED)
            .build();

    given(loadUserAccountPort.findDeletedByEmail("user@example.com"))
        .willReturn(Optional.of(kakaoDeletedAccount));

    assertThatThrownBy(() -> reactivateService.execute(command))
        .isInstanceOf(InvalidCredentialsException.class);
    verify(saveUserAccountPort, never()).save(any());
  }

  @Test
  @DisplayName("GOOGLE deleted user is reactivated and token is issued")
  void execute_googleDeletedUser_reactivatesAndIssuesToken() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.GOOGLE, null, null, "google-code", null);
    UserAccount deletedAccount =
        UserAccount.builder()
            .userId(4L)
            .provider(AuthProvider.GOOGLE)
            .providerUserId("google-provider-1")
            .status(AccountStatus.DELETED)
            .build();
    AccountUserSnapshot snapshot =
        new AccountUserSnapshot(4L, "google@example.com", "googleuser", null, "USER");

    given(reactivateService_googleAuthPort().getAccessToken("google-code"))
        .willReturn("google-access-token");
    given(reactivateService_googleAuthPort().getUserInfo("google-access-token"))
        .willReturn(
            GoogleUserInfo.builder()
                .providerUserId("google-provider-1")
                .email("google@example.com")
                .build());
    given(
            loadUserAccountPort.findDeletedByProviderAndProviderUserId(
                AuthProvider.GOOGLE, "google-provider-1"))
        .willReturn(Optional.of(deletedAccount));
    given(saveUserAccountPort.save(any(UserAccount.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(loadAccountUserInfoPort.findById(4L)).willReturn(Optional.of(snapshot));
    given(tokenIssuer.issueTokens(any(), any(), any()))
        .willReturn(new IssuedTokens("g-access", "g-refresh", "Bearer", 10L, 20L));
    given(loadUserWalletPort.loadActiveWalletAddress(4L)).willReturn(Optional.empty());

    LoginResult result = reactivateService.execute(command);

    assertThat(result.accessToken()).isEqualTo("g-access");
    verify(saveUserAccountPort).save(any(UserAccount.class));
  }

  @Test
  @DisplayName(
      "social active user throws AccountNotDeletedException when deleted user is not found")
  void execute_socialActiveUser_throwsAccountNotDeletedException() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.KAKAO, null, null, "kakao-code", null);
    UserAccount activeAccount =
        UserAccount.builder()
            .userId(5L)
            .provider(AuthProvider.KAKAO)
            .providerUserId("kakao-provider-2")
            .status(AccountStatus.ACTIVE)
            .build();

    given(kakaoAuthPort.getAccessToken("kakao-code")).willReturn("kakao-token");
    given(kakaoAuthPort.getUserInfo("kakao-token"))
        .willReturn(
            KakaoUserInfo.builder()
                .providerUserId("kakao-provider-2")
                .email("kakao@example.com")
                .build());
    given(
            loadUserAccountPort.findDeletedByProviderAndProviderUserId(
                AuthProvider.KAKAO, "kakao-provider-2"))
        .willReturn(Optional.empty());
    given(
            loadUserAccountPort.findByProviderAndProviderUserId(
                AuthProvider.KAKAO, "kakao-provider-2"))
        .willReturn(Optional.of(activeAccount));

    assertThatThrownBy(() -> reactivateService.execute(command))
        .isInstanceOf(AccountNotDeletedException.class);
    verify(saveUserAccountPort, never()).save(any());
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
    given(
            loadUserAccountPort.findDeletedByProviderAndProviderUserId(
                AuthProvider.KAKAO, "unknown-id"))
        .willReturn(Optional.empty());
    given(loadUserAccountPort.findByProviderAndProviderUserId(AuthProvider.KAKAO, "unknown-id"))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> reactivateService.execute(command))
        .isInstanceOf(UserNotFoundException.class);
    verify(saveUserAccountPort, never()).save(any());
  }

  @Test
  @DisplayName("wallet lookup throws exception - login succeeds with null walletAddress")
  void execute_walletLookupThrows_loginSucceedsWithNullWallet() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.LOCAL, "user@example.com", "raw-password", null, null);
    UserAccount deletedAccount =
        UserAccount.builder()
            .userId(1L)
            .provider(AuthProvider.LOCAL)
            .passwordHash("encoded-password")
            .status(AccountStatus.DELETED)
            .build();
    AccountUserSnapshot snapshot =
        new AccountUserSnapshot(1L, "user@example.com", "testuser", null, "USER");

    given(loadUserAccountPort.findDeletedByEmail("user@example.com"))
        .willReturn(Optional.of(deletedAccount));
    given(passwordEncoder.matches("raw-password", "encoded-password")).willReturn(true);
    given(saveUserAccountPort.save(any(UserAccount.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(loadAccountUserInfoPort.findById(1L)).willReturn(Optional.of(snapshot));
    given(tokenIssuer.issueTokens(any(), any(), any())).willReturn(STUB_TOKENS);
    given(loadUserWalletPort.loadActiveWalletAddress(1L))
        .willThrow(new RuntimeException("DB connection error"));

    LoginResult result = reactivateService.execute(command);

    assertThat(result.accessToken()).isEqualTo("access");
    assertThat(result.walletAddress()).isNull();
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
    UserAccount wrongProviderAccount =
        UserAccount.builder()
            .userId(2L)
            .provider(AuthProvider.GOOGLE)
            .providerUserId("provider-1")
            .status(AccountStatus.DELETED)
            .build();

    given(kakaoAuthPort.getAccessToken("auth-code")).willReturn("kakao-access-token");
    given(kakaoAuthPort.getUserInfo("kakao-access-token"))
        .willReturn(
            KakaoUserInfo.builder()
                .providerUserId("provider-1")
                .email("social@example.com")
                .build());
    given(
            loadUserAccountPort.findDeletedByProviderAndProviderUserId(
                AuthProvider.KAKAO, "provider-1"))
        .willReturn(Optional.of(wrongProviderAccount));

    assertThatThrownBy(() -> reactivateService.execute(command))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Invalid email or password");

    verify(saveUserAccountPort, never()).save(any(UserAccount.class));
    verifyNoInteractions(tokenIssuer);
  }
}
