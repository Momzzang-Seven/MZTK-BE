package momzzangseven.mztkbe.modules.auth.application.service;

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
import momzzangseven.mztkbe.modules.auth.application.dto.GoogleOAuthToken;
import momzzangseven.mztkbe.modules.auth.application.dto.GoogleUserInfo;
import momzzangseven.mztkbe.modules.auth.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.auth.application.dto.StepUpCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.StepUpResult;
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
@DisplayName("StepUpService unit test")
class StepUpServiceTest {

  @Mock private LoadUserPort loadUserPort;
  @Mock private SaveUserPort saveUserPort;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private KakaoAuthPort kakaoAuthPort;
  @Mock private GoogleAuthPort googleAuthPort;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private SensitiveTokenCipher sensitiveTokenCipher;

  @InjectMocks private StepUpService stepUpService;

  @Test
  @DisplayName("LOCAL step-up succeeds with valid password")
  void execute_localStepUp_succeeds() {
    User localUser = localUser();
    StepUpCommand command = StepUpCommand.of(1L, "raw-password", null);

    given(loadUserPort.loadUserById(1L)).willReturn(Optional.of(localUser));
    given(passwordEncoder.matches("raw-password", "encoded-password")).willReturn(true);
    given(jwtTokenProvider.generateStepUpAccessToken(1L, "user@example.com", UserRole.USER))
        .willReturn("stepup-access");
    given(jwtTokenProvider.getStepUpTokenExpiresIn()).willReturn(120L);

    StepUpResult result = stepUpService.execute(command);

    assertThat(result.accessToken()).isEqualTo("stepup-access");
    assertThat(result.grantType()).isEqualTo("Bearer");
    assertThat(result.expiresIn()).isEqualTo(120L);

    verify(jwtTokenProvider).generateStepUpAccessToken(1L, "user@example.com", UserRole.USER);
    verifyNoInteractions(kakaoAuthPort, googleAuthPort, saveUserPort, sensitiveTokenCipher);
  }

  @Test
  @DisplayName("LOCAL step-up rejects blank password")
  void execute_localStepUpBlankPassword_rejected() {
    StepUpCommand command = StepUpCommand.of(1L, " ", null);
    given(loadUserPort.loadUserById(1L)).willReturn(Optional.of(localUser()));

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("password is required for LOCAL step-up");

    verifyNoInteractions(passwordEncoder, kakaoAuthPort, googleAuthPort, saveUserPort);
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
    given(loadUserPort.loadUserById(1L)).willReturn(Optional.of(localUser()));
    given(passwordEncoder.matches("wrong-password", "encoded-password")).willReturn(false);

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Invalid password");

    verify(jwtTokenProvider, never())
        .generateStepUpAccessToken(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any());
    verifyNoInteractions(kakaoAuthPort, googleAuthPort, saveUserPort, sensitiveTokenCipher);
  }

  @Test
  @DisplayName("LOCAL step-up rejects unexpected authorization code")
  void execute_localStepUpWithAuthorizationCode_rejected() {
    StepUpCommand command = StepUpCommand.of(1L, "raw-password", "auth-code");
    given(loadUserPort.loadUserById(1L)).willReturn(Optional.of(localUser()));

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("authorizationCode must not be provided for LOCAL step-up");

    verifyNoInteractions(passwordEncoder, kakaoAuthPort, googleAuthPort, saveUserPort);
    verify(jwtTokenProvider, never())
        .generateStepUpAccessToken(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("GOOGLE step-up without new/stored refresh token requires offline consent")
  void execute_googleStepUpWithoutRefreshToken_throwsOfflineConsentRequired() {
    User googleUser = googleUser(2L, "google-provider-id", null);
    StepUpCommand command = StepUpCommand.of(2L, null, "auth-code");

    given(loadUserPort.loadUserById(2L)).willReturn(Optional.of(googleUser));
    given(googleAuthPort.exchangeToken("auth-code"))
        .willReturn(GoogleOAuthToken.of("google-access-token", null));
    given(googleAuthPort.getUserInfo("google-access-token"))
        .willReturn(GoogleUserInfo.builder().providerUserId("google-provider-id").build());

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.GOOGLE_OFFLINE_CONSENT_REQUIRED.getMessage());

    verify(saveUserPort, never()).saveUser(org.mockito.ArgumentMatchers.any(User.class));
    verifyNoInteractions(sensitiveTokenCipher);
  }

  @Test
  @DisplayName("step-up rejects when user is not found")
  void execute_userNotFound_throwsUserNotFoundException() {
    StepUpCommand command = StepUpCommand.of(999L, "pw", null);
    given(loadUserPort.loadUserById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(UserNotFoundException.class)
        .hasMessage("User not found with ID: 999");

    verifyNoInteractions(
        passwordEncoder, kakaoAuthPort, googleAuthPort, saveUserPort, jwtTokenProvider);
  }

  @Test
  @DisplayName("step-up rejects when auth provider is missing")
  void execute_providerMissing_throwsIllegalStateException() {
    User userWithoutProvider =
        User.builder()
            .id(3L)
            .email("noprov@example.com")
            .providerUserId("provider-id")
            .authProvider(null)
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .build();
    StepUpCommand command = StepUpCommand.of(3L, "pw", null);
    given(loadUserPort.loadUserById(3L)).willReturn(Optional.of(userWithoutProvider));

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("authProvider is missing");

    verifyNoInteractions(
        passwordEncoder, kakaoAuthPort, googleAuthPort, saveUserPort, jwtTokenProvider);
  }

  @Test
  @DisplayName("GOOGLE step-up rejects blank authorization code")
  void execute_googleStepUpBlankAuthorizationCode_rejected() {
    StepUpCommand command = StepUpCommand.of(2L, null, " ");
    given(loadUserPort.loadUserById(2L)).willReturn(Optional.of(googleUser(2L, "google-id", null)));

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("authorizationCode is required for GOOGLE step-up");

    verifyNoInteractions(googleAuthPort, kakaoAuthPort, saveUserPort, sensitiveTokenCipher);
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
    given(loadUserPort.loadUserById(2L)).willReturn(Optional.of(googleUser(2L, "google-id", null)));

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("password must not be provided for GOOGLE step-up");

    verifyNoInteractions(googleAuthPort, kakaoAuthPort, saveUserPort, sensitiveTokenCipher);
  }

  @Test
  @DisplayName("KAKAO step-up succeeds when provider user id matches")
  void execute_kakaoStepUp_succeeds() {
    StepUpCommand command = StepUpCommand.of(4L, null, "kakao-code");
    User kakaoUser = kakaoUser(4L, "kakao-user-id");

    given(loadUserPort.loadUserById(4L)).willReturn(Optional.of(kakaoUser));
    given(kakaoAuthPort.getAccessToken("kakao-code")).willReturn("kakao-access");
    given(kakaoAuthPort.getUserInfo("kakao-access"))
        .willReturn(KakaoUserInfo.builder().providerUserId("kakao-user-id").build());
    given(jwtTokenProvider.generateStepUpAccessToken(4L, "kakao@example.com", UserRole.USER))
        .willReturn("kakao-stepup-token");
    given(jwtTokenProvider.getStepUpTokenExpiresIn()).willReturn(180L);

    StepUpResult result = stepUpService.execute(command);

    assertThat(result.accessToken()).isEqualTo("kakao-stepup-token");
    assertThat(result.expiresIn()).isEqualTo(180L);
    verifyNoInteractions(saveUserPort, sensitiveTokenCipher, googleAuthPort);
  }

  @Test
  @DisplayName("KAKAO step-up rejects when provider user id does not match")
  void execute_kakaoStepUpProviderMismatch_rejected() {
    StepUpCommand command = StepUpCommand.of(4L, null, "kakao-code");
    User kakaoUser = kakaoUser(4L, "expected-kakao-id");

    given(loadUserPort.loadUserById(4L)).willReturn(Optional.of(kakaoUser));
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
    User googleUser = googleUser(5L, "google-user-id", null);

    given(loadUserPort.loadUserById(5L)).willReturn(Optional.of(googleUser));
    given(googleAuthPort.exchangeToken("google-code"))
        .willReturn(GoogleOAuthToken.of("google-access", "raw-refresh-token"));
    given(googleAuthPort.getUserInfo("google-access"))
        .willReturn(GoogleUserInfo.builder().providerUserId("google-user-id").build());
    given(sensitiveTokenCipher.encrypt("raw-refresh-token")).willReturn("enc-refresh-token");
    given(saveUserPort.saveUser(org.mockito.ArgumentMatchers.any(User.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(jwtTokenProvider.generateStepUpAccessToken(5L, "google@example.com", UserRole.USER))
        .willReturn("google-stepup-token");
    given(jwtTokenProvider.getStepUpTokenExpiresIn()).willReturn(300L);

    StepUpResult result = stepUpService.execute(command);

    assertThat(result.accessToken()).isEqualTo("google-stepup-token");
    verify(sensitiveTokenCipher).encrypt("raw-refresh-token");
    verify(saveUserPort)
        .saveUser(argThat(saved -> "enc-refresh-token".equals(saved.getGoogleRefreshToken())));
  }

  @Test
  @DisplayName("GOOGLE step-up with stored refresh token succeeds without save")
  void execute_googleStepUpWithStoredRefreshToken_succeedsWithoutSaving() {
    StepUpCommand command = StepUpCommand.of(6L, null, "google-code");
    User googleUser = googleUser(6L, "google-user-id", "stored-encrypted-refresh");

    given(loadUserPort.loadUserById(6L)).willReturn(Optional.of(googleUser));
    given(googleAuthPort.exchangeToken("google-code"))
        .willReturn(GoogleOAuthToken.of("google-access", null));
    given(googleAuthPort.getUserInfo("google-access"))
        .willReturn(GoogleUserInfo.builder().providerUserId("google-user-id").build());
    given(jwtTokenProvider.generateStepUpAccessToken(6L, "google@example.com", UserRole.USER))
        .willReturn("google-stepup-token");
    given(jwtTokenProvider.getStepUpTokenExpiresIn()).willReturn(300L);

    StepUpResult result = stepUpService.execute(command);

    assertThat(result.accessToken()).isEqualTo("google-stepup-token");
    verify(saveUserPort, never()).saveUser(org.mockito.ArgumentMatchers.any(User.class));
    verifyNoInteractions(sensitiveTokenCipher);
  }

  @Test
  @DisplayName("GOOGLE step-up with blank new refresh token uses stored refresh token")
  void execute_googleStepUpWithBlankNewRefreshToken_succeedsWithoutSaving() {
    StepUpCommand command = StepUpCommand.of(6L, null, "google-code");
    User googleUser = googleUser(6L, "google-user-id", "stored-encrypted-refresh");

    given(loadUserPort.loadUserById(6L)).willReturn(Optional.of(googleUser));
    given(googleAuthPort.exchangeToken("google-code"))
        .willReturn(GoogleOAuthToken.of("google-access", " "));
    given(googleAuthPort.getUserInfo("google-access"))
        .willReturn(GoogleUserInfo.builder().providerUserId("google-user-id").build());
    given(jwtTokenProvider.generateStepUpAccessToken(6L, "google@example.com", UserRole.USER))
        .willReturn("google-stepup-token");
    given(jwtTokenProvider.getStepUpTokenExpiresIn()).willReturn(300L);

    StepUpResult result = stepUpService.execute(command);

    assertThat(result.accessToken()).isEqualTo("google-stepup-token");
    verify(saveUserPort, never()).saveUser(org.mockito.ArgumentMatchers.any(User.class));
    verifyNoInteractions(sensitiveTokenCipher);
  }

  @Test
  @DisplayName("GOOGLE step-up with blank stored refresh token requires offline consent")
  void execute_googleStepUpWithBlankStoredRefreshToken_throwsOfflineConsentRequired() {
    StepUpCommand command = StepUpCommand.of(6L, null, "google-code");
    User googleUser = googleUser(6L, "google-user-id", " ");

    given(loadUserPort.loadUserById(6L)).willReturn(Optional.of(googleUser));
    given(googleAuthPort.exchangeToken("google-code"))
        .willReturn(GoogleOAuthToken.of("google-access", null));
    given(googleAuthPort.getUserInfo("google-access"))
        .willReturn(GoogleUserInfo.builder().providerUserId("google-user-id").build());

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.GOOGLE_OFFLINE_CONSENT_REQUIRED.getMessage());

    verify(saveUserPort, never()).saveUser(org.mockito.ArgumentMatchers.any(User.class));
    verifyNoInteractions(sensitiveTokenCipher);
  }

  @Test
  @DisplayName("GOOGLE step-up rejects when provider user id is missing on user")
  void execute_googleStepUpWithMissingProviderUserId_rejected() {
    StepUpCommand command = StepUpCommand.of(7L, null, "google-code");
    User googleUser = googleUser(7L, null, "stored-encrypted-refresh");

    given(loadUserPort.loadUserById(7L)).willReturn(Optional.of(googleUser));
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
    User kakaoUser = kakaoUser(8L, " ");

    given(loadUserPort.loadUserById(8L)).willReturn(Optional.of(kakaoUser));
    given(kakaoAuthPort.getAccessToken("kakao-code")).willReturn("kakao-access");
    given(kakaoAuthPort.getUserInfo("kakao-access"))
        .willReturn(KakaoUserInfo.builder().providerUserId("actual-kakao-id").build());

    assertThatThrownBy(() -> stepUpService.execute(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("providerUserId is missing for KAKAO user");
  }

  private User localUser() {
    return User.builder()
        .id(1L)
        .email("user@example.com")
        .password("encoded-password")
        .authProvider(AuthProvider.LOCAL)
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .build();
  }

  private User googleUser(Long id, String providerUserId, String encryptedRefreshToken) {
    return User.builder()
        .id(id)
        .email("google@example.com")
        .providerUserId(providerUserId)
        .authProvider(AuthProvider.GOOGLE)
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .googleRefreshToken(encryptedRefreshToken)
        .build();
  }

  private User kakaoUser(Long id, String providerUserId) {
    return User.builder()
        .id(id)
        .email("kakao@example.com")
        .providerUserId(providerUserId)
        .authProvider(AuthProvider.KAKAO)
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .build();
  }
}
