package momzzangseven.mztkbe.modules.account.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.global.error.UnsupportedProviderException;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.global.security.SensitiveTokenCipher;
import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.account.application.dto.GoogleOAuthToken;
import momzzangseven.mztkbe.modules.account.application.dto.GoogleUserInfo;
import momzzangseven.mztkbe.modules.account.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.account.application.dto.StepUpCommand;
import momzzangseven.mztkbe.modules.account.application.dto.StepUpResult;
import momzzangseven.mztkbe.modules.account.application.port.in.StepUpUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountUserInfoPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class StepUpService implements StepUpUseCase {

  private final LoadUserAccountPort loadUserAccountPort;
  private final SaveUserAccountPort saveUserAccountPort;
  private final LoadAccountUserInfoPort loadAccountUserInfoPort;
  private final PasswordEncoder passwordEncoder;
  private final KakaoAuthPort kakaoAuthPort;
  private final GoogleAuthPort googleAuthPort;
  private final JwtTokenProvider jwtTokenProvider;
  private final SensitiveTokenCipher sensitiveTokenCipher;

  @Override
  public StepUpResult execute(StepUpCommand command) {
    log.info("Step-up request received: userId={}", command.userId());

    command.validate();
    log.debug("Command validation passed");

    UserAccount account =
        loadUserAccountPort
            .findByUserId(command.userId())
            .orElseThrow(() -> new UserNotFoundException(command.userId()));
    AuthProvider provider = requireProvider(account);
    log.debug("Loaded account for step-up: userId={}, provider={}", account.getUserId(), provider);

    validateCredentialInput(provider, command.password(), command.authorizationCode());
    verifyCredential(account, provider, command.password(), command.authorizationCode());
    log.debug(
        "Step-up credential verification passed: userId={}, provider={}",
        account.getUserId(),
        provider);

    AccountUserSnapshot snapshot =
        loadAccountUserInfoPort
            .findById(command.userId())
            .orElseThrow(() -> new UserNotFoundException(command.userId()));

    String accessToken =
        jwtTokenProvider.generateStepUpAccessToken(
            snapshot.userId(), snapshot.email(), UserRole.valueOf(snapshot.role()));

    StepUpResult result = StepUpResult.of(accessToken, jwtTokenProvider.getStepUpTokenExpiresIn());
    log.info(
        "Step-up authentication successful: userId={}, provider={}", account.getUserId(), provider);
    return result;
  }

  private AuthProvider requireProvider(UserAccount account) {
    AuthProvider provider = account.getProvider();
    if (provider == null) {
      throw new IllegalStateException("authProvider is missing for userId=" + account.getUserId());
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
      UserAccount account, AuthProvider provider, String password, String authorizationCode) {
    switch (provider) {
      case LOCAL -> verifyLocalPassword(account, password);
      case KAKAO -> verifyKakao(account, authorizationCode.trim());
      case GOOGLE -> verifyGoogle(account, authorizationCode.trim());
      default -> throw new UnsupportedProviderException(provider);
    }
  }

  private void verifyLocalPassword(UserAccount account, String password) {
    if (!passwordEncoder.matches(password, account.getPasswordHash())) {
      throw new InvalidCredentialsException("Invalid password");
    }
  }

  private void verifyKakao(UserAccount account, String authorizationCode) {
    String accessToken = kakaoAuthPort.getAccessToken(authorizationCode);
    KakaoUserInfo info = kakaoAuthPort.getUserInfo(accessToken);
    verifyProviderUserId(AuthProvider.KAKAO, account.getProviderUserId(), info.getProviderUserId());
  }

  private void verifyGoogle(UserAccount account, String authorizationCode) {
    GoogleOAuthToken token = googleAuthPort.exchangeToken(authorizationCode);
    GoogleUserInfo info = googleAuthPort.getUserInfo(token.accessToken());
    verifyProviderUserId(
        AuthProvider.GOOGLE, account.getProviderUserId(), info.getProviderUserId());
    saveGoogleRefreshTokenIfPresent(account, token.refreshToken());
  }

  private void saveGoogleRefreshTokenIfPresent(UserAccount account, String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      if (account.getGoogleRefreshToken() == null || account.getGoogleRefreshToken().isBlank()) {
        log.info(
            "Google refresh token missing and not stored; offline consent required: userId={}",
            account.getUserId());
        throw new BusinessException(ErrorCode.GOOGLE_OFFLINE_CONSENT_REQUIRED);
      }
      return;
    }

    String encrypted = sensitiveTokenCipher.encrypt(refreshToken);
    saveUserAccountPort.save(account.updateGoogleRefreshToken(encrypted));
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
