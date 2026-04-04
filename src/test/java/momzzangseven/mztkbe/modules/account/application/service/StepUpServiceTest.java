package momzzangseven.mztkbe.modules.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.global.security.SensitiveTokenCipher;
import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.account.application.dto.GoogleOAuthToken;
import momzzangseven.mztkbe.modules.account.application.dto.GoogleUserInfo;
import momzzangseven.mztkbe.modules.account.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.account.application.dto.StepUpCommand;
import momzzangseven.mztkbe.modules.account.application.dto.StepUpResult;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountUserInfoPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("StepUpService unit test")
class StepUpServiceTest {

  @Mock private LoadUserAccountPort loadUserAccountPort;
  @Mock private SaveUserAccountPort saveUserAccountPort;
  @Mock private LoadAccountUserInfoPort loadAccountUserInfoPort;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private KakaoAuthPort kakaoAuthPort;
  @Mock private GoogleAuthPort googleAuthPort;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private SensitiveTokenCipher sensitiveTokenCipher;

  @InjectMocks private StepUpService stepUpService;

  @Test
  @DisplayName("LOCAL step-up succeeds with valid password")
  void execute_localStepUp_succeeds() {
    UserAccount localAccount = localAccount();
    AccountUserSnapshot snapshot =
        new AccountUserSnapshot(1L, "user@example.com", "testuser", null, "USER");
    StepUpCommand command = StepUpCommand.of(1L, "raw-password", null);

    given(loadUserAccountPort.findByUserId(1L)).willReturn(Optional.of(localAccount));
    given(passwordEncoder.matches("raw-password", "encoded-password")).willReturn(true);
    given(loadAccountUserInfoPort.findById(1L)).willReturn(Optional.of(snapshot));
    given(jwtTokenProvider.generateStepUpAccessToken(1L, "user@example.com", UserRole.USER))
        .willReturn("stepup-access");
    given(jwtTokenProvider.getStepUpTokenExpiresIn()).willReturn(120L);

    StepUpResult result = stepUpService.execute(command);

    assertThat(result.accessToken()).isEqualTo("stepup-access");
    assertThat(result.grantType()).isEqualTo("Bearer");
    assertThat(result.expiresIn()).isEqualTo(120L);

    verify(jwtTokenProvider).generateStepUpAccessToken(1L, "user@example.com", UserRole.USER);
    verifyNoInteractions(kakaoAuthPort, googleAuthPort, saveUserAccountPort, sensitiveTokenCipher);
  }

  @Test
  @DisplayName("LOCAL step-up rejects blank password")
  void execute_localStepUpBlankPassword_rejected() {
    StepUpCommand command = StepUpCommand.of(1L, " ", null);
    given(loadUserAccountPort.findByUserId(1L)).willReturn(Optional.of(localAccount()));

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("password is required for LOCAL step-up");

    verifyNoInteractions(passwordEncoder, kakaoAuthPort, googleAuthPort, saveUserAccountPort);
    verify(jwtTokenProvider, never())
        .generateStepUpAccessToken(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("LOCAL step-up rejects invalid password")
  void execute_localStepUpInvalidPassword_rejected() {
    StepUpCommand command = StepUpCommand.of(1L, "wrong-password", null);
    given(loadUserAccountPort.findByUserId(1L)).willReturn(Optional.of(localAccount()));
    given(passwordEncoder.matches("wrong-password", "encoded-password")).willReturn(false);

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Invalid password");

    verify(jwtTokenProvider, never())
        .generateStepUpAccessToken(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any());
    verifyNoInteractions(kakaoAuthPort, googleAuthPort, saveUserAccountPort, sensitiveTokenCipher);
  }

  @Test
  @DisplayName("LOCAL step-up rejects unexpected authorization code")
  void execute_localStepUpWithAuthorizationCode_rejected() {
    StepUpCommand command = StepUpCommand.of(1L, "raw-password", "auth-code");
    given(loadUserAccountPort.findByUserId(1L)).willReturn(Optional.of(localAccount()));

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("authorizationCode must not be provided for LOCAL step-up");

    verifyNoInteractions(passwordEncoder, kakaoAuthPort, googleAuthPort, saveUserAccountPort);
    verify(jwtTokenProvider, never())
        .generateStepUpAccessToken(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("GOOGLE step-up without new/stored refresh token requires offline consent")
  void execute_googleStepUpWithoutRefreshToken_throwsOfflineConsentRequired() {
    UserAccount googleAccount = googleAccount(2L, "google-provider-id", null);
    StepUpCommand command = StepUpCommand.of(2L, null, "auth-code");

    given(loadUserAccountPort.findByUserId(2L)).willReturn(Optional.of(googleAccount));
    given(googleAuthPort.exchangeToken("auth-code"))
        .willReturn(GoogleOAuthToken.of("google-access-token", null));
    given(googleAuthPort.getUserInfo("google-access-token"))
        .willReturn(GoogleUserInfo.builder().providerUserId("google-provider-id").build());

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.GOOGLE_OFFLINE_CONSENT_REQUIRED.getMessage());

    verify(saveUserAccountPort, never()).save(org.mockito.ArgumentMatchers.any(UserAccount.class));
    verifyNoInteractions(sensitiveTokenCipher);
  }

  @Test
  @DisplayName("step-up rejects when user is not found")
  void execute_userNotFound_throwsUserNotFoundException() {
    StepUpCommand command = StepUpCommand.of(999L, "pw", null);
    given(loadUserAccountPort.findByUserId(999L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(UserNotFoundException.class)
        .hasMessage("User not found with ID: 999");

    verifyNoInteractions(
        passwordEncoder, kakaoAuthPort, googleAuthPort, saveUserAccountPort, jwtTokenProvider);
  }

  @Test
  @DisplayName("step-up rejects when auth provider is missing")
  void execute_providerMissing_throwsIllegalStateException() {
    UserAccount accountWithoutProvider =
        UserAccount.builder()
            .userId(3L)
            .providerUserId("provider-id")
            .provider(null)
            .status(AccountStatus.ACTIVE)
            .build();
    StepUpCommand command = StepUpCommand.of(3L, "pw", null);
    given(loadUserAccountPort.findByUserId(3L)).willReturn(Optional.of(accountWithoutProvider));

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("authProvider is missing");

    verifyNoInteractions(
        passwordEncoder, kakaoAuthPort, googleAuthPort, saveUserAccountPort, jwtTokenProvider);
  }

  @Test
  @DisplayName("GOOGLE step-up rejects blank authorization code")
  void execute_googleStepUpBlankAuthorizationCode_rejected() {
    StepUpCommand command = StepUpCommand.of(2L, null, " ");
    given(loadUserAccountPort.findByUserId(2L))
        .willReturn(Optional.of(googleAccount(2L, "google-id", null)));

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("authorizationCode is required for GOOGLE step-up");

    verifyNoInteractions(googleAuthPort, kakaoAuthPort, saveUserAccountPort, sensitiveTokenCipher);
    verify(jwtTokenProvider, never())
        .generateStepUpAccessToken(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("GOOGLE step-up rejects password input")
  void execute_googleStepUpWithPassword_rejected() {
    StepUpCommand command = StepUpCommand.of(2L, "pw", "auth-code");
    given(loadUserAccountPort.findByUserId(2L))
        .willReturn(Optional.of(googleAccount(2L, "google-id", null)));

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("password must not be provided for GOOGLE step-up");

    verifyNoInteractions(googleAuthPort, kakaoAuthPort, saveUserAccountPort, sensitiveTokenCipher);
  }

  @Test
  @DisplayName("KAKAO step-up succeeds when provider user id matches")
  void execute_kakaoStepUp_succeeds() {
    StepUpCommand command = StepUpCommand.of(4L, null, "kakao-code");
    UserAccount kakaoAccount = kakaoAccount(4L, "kakao-user-id");
    AccountUserSnapshot snapshot =
        new AccountUserSnapshot(4L, "kakao@example.com", "kakaouser", null, "USER");

    given(loadUserAccountPort.findByUserId(4L)).willReturn(Optional.of(kakaoAccount));
    given(kakaoAuthPort.getAccessToken("kakao-code")).willReturn("kakao-access");
    given(kakaoAuthPort.getUserInfo("kakao-access"))
        .willReturn(KakaoUserInfo.builder().providerUserId("kakao-user-id").build());
    given(loadAccountUserInfoPort.findById(4L)).willReturn(Optional.of(snapshot));
    given(jwtTokenProvider.generateStepUpAccessToken(4L, "kakao@example.com", UserRole.USER))
        .willReturn("kakao-stepup-token");
    given(jwtTokenProvider.getStepUpTokenExpiresIn()).willReturn(180L);

    StepUpResult result = stepUpService.execute(command);

    assertThat(result.accessToken()).isEqualTo("kakao-stepup-token");
    assertThat(result.expiresIn()).isEqualTo(180L);
    verifyNoInteractions(saveUserAccountPort, sensitiveTokenCipher, googleAuthPort);
  }

  @Test
  @DisplayName("KAKAO step-up rejects when provider user id does not match")
  void execute_kakaoStepUpProviderMismatch_rejected() {
    StepUpCommand command = StepUpCommand.of(4L, null, "kakao-code");
    UserAccount kakaoAccount = kakaoAccount(4L, "expected-kakao-id");

    given(loadUserAccountPort.findByUserId(4L)).willReturn(Optional.of(kakaoAccount));
    given(kakaoAuthPort.getAccessToken("kakao-code")).willReturn("kakao-access");
    given(kakaoAuthPort.getUserInfo("kakao-access"))
        .willReturn(KakaoUserInfo.builder().providerUserId("actual-kakao-id").build());

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("KAKAO account does not match authenticated user");

    verify(jwtTokenProvider, never())
        .generateStepUpAccessToken(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("GOOGLE step-up with new refresh token encrypts and stores it")
  void execute_googleStepUpWithNewRefreshToken_savesEncryptedToken() {
    StepUpCommand command = StepUpCommand.of(5L, null, "google-code");
    UserAccount googleAccount = googleAccount(5L, "google-user-id", null);
    AccountUserSnapshot snapshot =
        new AccountUserSnapshot(5L, "google@example.com", "googleuser", null, "USER");

    given(loadUserAccountPort.findByUserId(5L)).willReturn(Optional.of(googleAccount));
    given(googleAuthPort.exchangeToken("google-code"))
        .willReturn(GoogleOAuthToken.of("google-access", "raw-refresh-token"));
    given(googleAuthPort.getUserInfo("google-access"))
        .willReturn(GoogleUserInfo.builder().providerUserId("google-user-id").build());
    given(sensitiveTokenCipher.encrypt("raw-refresh-token")).willReturn("enc-refresh-token");
    given(saveUserAccountPort.save(org.mockito.ArgumentMatchers.any(UserAccount.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(loadAccountUserInfoPort.findById(5L)).willReturn(Optional.of(snapshot));
    given(jwtTokenProvider.generateStepUpAccessToken(5L, "google@example.com", UserRole.USER))
        .willReturn("google-stepup-token");
    given(jwtTokenProvider.getStepUpTokenExpiresIn()).willReturn(300L);

    StepUpResult result = stepUpService.execute(command);

    assertThat(result.accessToken()).isEqualTo("google-stepup-token");
    verify(sensitiveTokenCipher).encrypt("raw-refresh-token");
    verify(saveUserAccountPort)
        .save(argThat(saved -> "enc-refresh-token".equals(saved.getGoogleRefreshToken())));
  }

  @Test
  @DisplayName("GOOGLE step-up with stored refresh token succeeds without save")
  void execute_googleStepUpWithStoredRefreshToken_succeedsWithoutSaving() {
    StepUpCommand command = StepUpCommand.of(6L, null, "google-code");
    UserAccount googleAccount = googleAccount(6L, "google-user-id", "stored-encrypted-refresh");
    AccountUserSnapshot snapshot =
        new AccountUserSnapshot(6L, "google@example.com", "googleuser", null, "USER");

    given(loadUserAccountPort.findByUserId(6L)).willReturn(Optional.of(googleAccount));
    given(googleAuthPort.exchangeToken("google-code"))
        .willReturn(GoogleOAuthToken.of("google-access", null));
    given(googleAuthPort.getUserInfo("google-access"))
        .willReturn(GoogleUserInfo.builder().providerUserId("google-user-id").build());
    given(loadAccountUserInfoPort.findById(6L)).willReturn(Optional.of(snapshot));
    given(jwtTokenProvider.generateStepUpAccessToken(6L, "google@example.com", UserRole.USER))
        .willReturn("google-stepup-token");
    given(jwtTokenProvider.getStepUpTokenExpiresIn()).willReturn(300L);

    StepUpResult result = stepUpService.execute(command);

    assertThat(result.accessToken()).isEqualTo("google-stepup-token");
    verify(saveUserAccountPort, never()).save(org.mockito.ArgumentMatchers.any(UserAccount.class));
    verifyNoInteractions(sensitiveTokenCipher);
  }

  @Test
  @DisplayName("GOOGLE step-up with blank new refresh token uses stored refresh token")
  void execute_googleStepUpWithBlankNewRefreshToken_succeedsWithoutSaving() {
    StepUpCommand command = StepUpCommand.of(6L, null, "google-code");
    UserAccount googleAccount = googleAccount(6L, "google-user-id", "stored-encrypted-refresh");
    AccountUserSnapshot snapshot =
        new AccountUserSnapshot(6L, "google@example.com", "googleuser", null, "USER");

    given(loadUserAccountPort.findByUserId(6L)).willReturn(Optional.of(googleAccount));
    given(googleAuthPort.exchangeToken("google-code"))
        .willReturn(GoogleOAuthToken.of("google-access", " "));
    given(googleAuthPort.getUserInfo("google-access"))
        .willReturn(GoogleUserInfo.builder().providerUserId("google-user-id").build());
    given(loadAccountUserInfoPort.findById(6L)).willReturn(Optional.of(snapshot));
    given(jwtTokenProvider.generateStepUpAccessToken(6L, "google@example.com", UserRole.USER))
        .willReturn("google-stepup-token");
    given(jwtTokenProvider.getStepUpTokenExpiresIn()).willReturn(300L);

    StepUpResult result = stepUpService.execute(command);

    assertThat(result.accessToken()).isEqualTo("google-stepup-token");
    verify(saveUserAccountPort, never()).save(org.mockito.ArgumentMatchers.any(UserAccount.class));
    verifyNoInteractions(sensitiveTokenCipher);
  }

  @Test
  @DisplayName("GOOGLE step-up with blank stored refresh token requires offline consent")
  void execute_googleStepUpWithBlankStoredRefreshToken_throwsOfflineConsentRequired() {
    StepUpCommand command = StepUpCommand.of(6L, null, "google-code");
    UserAccount googleAccount = googleAccount(6L, "google-user-id", " ");

    given(loadUserAccountPort.findByUserId(6L)).willReturn(Optional.of(googleAccount));
    given(googleAuthPort.exchangeToken("google-code"))
        .willReturn(GoogleOAuthToken.of("google-access", null));
    given(googleAuthPort.getUserInfo("google-access"))
        .willReturn(GoogleUserInfo.builder().providerUserId("google-user-id").build());

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.GOOGLE_OFFLINE_CONSENT_REQUIRED.getMessage());

    verify(saveUserAccountPort, never()).save(org.mockito.ArgumentMatchers.any(UserAccount.class));
    verifyNoInteractions(sensitiveTokenCipher);
  }

  @Test
  @DisplayName("GOOGLE step-up rejects when provider user id is missing on user")
  void execute_googleStepUpWithMissingProviderUserId_rejected() {
    StepUpCommand command = StepUpCommand.of(7L, null, "google-code");
    UserAccount googleAccount = googleAccount(7L, null, "stored-encrypted-refresh");

    given(loadUserAccountPort.findByUserId(7L)).willReturn(Optional.of(googleAccount));
    given(googleAuthPort.exchangeToken("google-code"))
        .willReturn(GoogleOAuthToken.of("google-access", "raw-refresh-token"));
    given(googleAuthPort.getUserInfo("google-access"))
        .willReturn(GoogleUserInfo.builder().providerUserId("google-user-id").build());

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("providerUserId is missing for GOOGLE user");

    verify(jwtTokenProvider, never())
        .generateStepUpAccessToken(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("KAKAO step-up rejects when provider user id is blank on user")
  void execute_kakaoStepUpWithBlankProviderUserId_rejected() {
    StepUpCommand command = StepUpCommand.of(8L, null, "kakao-code");
    UserAccount kakaoAccount = kakaoAccount(8L, " ");

    given(loadUserAccountPort.findByUserId(8L)).willReturn(Optional.of(kakaoAccount));
    given(kakaoAuthPort.getAccessToken("kakao-code")).willReturn("kakao-access");
    given(kakaoAuthPort.getUserInfo("kakao-access"))
        .willReturn(KakaoUserInfo.builder().providerUserId("actual-kakao-id").build());

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("providerUserId is missing for KAKAO user");
  }

  private UserAccount localAccount() {
    return UserAccount.builder()
        .userId(1L)
        .provider(AuthProvider.LOCAL)
        .passwordHash("encoded-password")
        .status(AccountStatus.ACTIVE)
        .build();
  }

  private UserAccount googleAccount(
      Long userId, String providerUserId, String encryptedRefreshToken) {
    return UserAccount.builder()
        .userId(userId)
        .providerUserId(providerUserId)
        .provider(AuthProvider.GOOGLE)
        .status(AccountStatus.ACTIVE)
        .googleRefreshToken(encryptedRefreshToken)
        .build();
  }

  private UserAccount kakaoAccount(Long userId, String providerUserId) {
    return UserAccount.builder()
        .userId(userId)
        .providerUserId(providerUserId)
        .provider(AuthProvider.KAKAO)
        .status(AccountStatus.ACTIVE)
        .build();
  }
}
