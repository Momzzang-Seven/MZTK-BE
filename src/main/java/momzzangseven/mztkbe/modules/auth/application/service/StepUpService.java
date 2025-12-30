package momzzangseven.mztkbe.modules.auth.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.global.error.UnsupportedProviderException;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.global.security.SensitiveTokenCipher;
import momzzangseven.mztkbe.modules.auth.application.dto.GoogleOAuthToken;
import momzzangseven.mztkbe.modules.auth.application.dto.GoogleUserInfo;
import momzzangseven.mztkbe.modules.auth.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.auth.application.dto.StepUpCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.StepUpResult;
import momzzangseven.mztkbe.modules.auth.application.port.in.StepUpUseCase;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class StepUpService implements StepUpUseCase {

  private final LoadUserPort loadUserPort;
  private final SaveUserPort saveUserPort;
  private final PasswordEncoder passwordEncoder;
  private final KakaoAuthPort kakaoAuthPort;
  private final GoogleAuthPort googleAuthPort;
  private final JwtTokenProvider jwtTokenProvider;
  private final SensitiveTokenCipher sensitiveTokenCipher;

  @Override
  public StepUpResult execute(StepUpCommand command) {
    log.info("Step-up request received: userId={}", command.userId());

    // Step 1: Validate Command
    command.validate();
    log.debug("Command validation passed");

    // Step 2: Load User
    User user =
        loadUserPort
            .loadUserById(command.userId())
            .orElseThrow(() -> new UserNotFoundException(command.userId()));
    AuthProvider provider = requireProvider(user);
    log.debug("Loaded user for step-up: userId={}, provider={}", user.getId(), provider);

    // Step 3: Validate Credential Input (provider-based)
    validateCredentialInput(provider, command.password(), command.authorizationCode());

    // Step 4: Verify Credential
    verifyCredential(user, provider, command.password(), command.authorizationCode());
    log.debug(
        "Step-up credential verification passed: userId={}, provider={}", user.getId(), provider);

    // Step 5: Issue Step-up Token
    long ttlMillis = jwtTokenProvider.getStepUpTokenExpiresIn();
    String accessToken =
        jwtTokenProvider.generateStepUpAccessToken(
            user.getId(), user.getEmail(), user.getRole(), ttlMillis);

    // Step 6: Build and Return Result
    StepUpResult result = StepUpResult.of(accessToken, ttlMillis);
    log.info("Step-up authentication successful: userId={}, provider={}", user.getId(), provider);
    return result;
  }

  private AuthProvider requireProvider(User user) {
    AuthProvider provider = user.getAuthProvider();
    if (provider == null) {
      throw new IllegalStateException("authProvider is missing for userId=" + user.getId());
    }
    return provider;
  }

  private void validateCredentialInput(
      AuthProvider provider, String password, String authorizationCode) {
    boolean hasPassword = password != null && !password.isBlank();
    boolean hasAuthorizationCode = authorizationCode != null && !authorizationCode.isBlank();

    switch (provider) {
      case LOCAL -> {
        if (!hasPassword) {
          throw new IllegalArgumentException("password is required for LOCAL step-up");
        }
        if (hasAuthorizationCode) {
          throw new IllegalArgumentException(
              "authorizationCode must not be provided for LOCAL step-up");
        }
      }
      case KAKAO, GOOGLE -> {
        if (!hasAuthorizationCode) {
          throw new IllegalArgumentException(
              "authorizationCode is required for " + provider + " step-up");
        }
        if (hasPassword) {
          throw new IllegalArgumentException(
              "password must not be provided for " + provider + " step-up");
        }
      }
      default -> throw new UnsupportedProviderException(provider);
    }
  }

  private void verifyCredential(
      User user, AuthProvider provider, String password, String authorizationCode) {
    switch (provider) {
      case LOCAL -> verifyLocalPassword(user, password);
      case KAKAO -> verifyKakao(user, authorizationCode.trim());
      case GOOGLE -> verifyGoogle(user, authorizationCode.trim());
      default -> throw new UnsupportedProviderException(provider);
    }
  }

  private void verifyLocalPassword(User user, String password) {
    if (!user.validatePassword(password, passwordEncoder)) {
      throw new InvalidCredentialsException("Invalid password");
    }
  }

  private void verifyKakao(User user, String authorizationCode) {
    String accessToken = kakaoAuthPort.getAccessToken(authorizationCode);
    KakaoUserInfo info = kakaoAuthPort.getUserInfo(accessToken);
    verifyProviderUserId(AuthProvider.KAKAO, user.getProviderUserId(), info.getProviderUserId());
  }

  private void verifyGoogle(User user, String authorizationCode) {
    GoogleOAuthToken token = googleAuthPort.exchangeToken(authorizationCode);
    GoogleUserInfo info = googleAuthPort.getUserInfo(token.accessToken());
    verifyProviderUserId(AuthProvider.GOOGLE, user.getProviderUserId(), info.getProviderUserId());
    saveGoogleRefreshTokenIfPresent(user, token.refreshToken());
  }

  private void saveGoogleRefreshTokenIfPresent(User user, String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      if (user.getGoogleRefreshToken() == null || user.getGoogleRefreshToken().isBlank()) {
        log.info(
            "Google refresh token missing and not stored; offline consent required: userId={}",
            user.getId());
        throw new BusinessException(ErrorCode.GOOGLE_OFFLINE_CONSENT_REQUIRED);
      }
      return;
    }

    String encrypted = sensitiveTokenCipher.encrypt(refreshToken);
    saveUserPort.saveUser(user.updateGoogleRefreshToken(encrypted));
  }

  private void verifyProviderUserId(
      AuthProvider provider, String expectedProviderUserId, String actualProviderUserId) {
    if (expectedProviderUserId == null || expectedProviderUserId.isBlank()) {
      throw new IllegalStateException("providerUserId is missing for " + provider + " user");
    }

    if (!expectedProviderUserId.equals(actualProviderUserId)) {
      throw new InvalidCredentialsException(
          provider + " account does not match authenticated user");
    }
  }
}
