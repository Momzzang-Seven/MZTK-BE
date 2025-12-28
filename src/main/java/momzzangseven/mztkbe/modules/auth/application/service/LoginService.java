package momzzangseven.mztkbe.modules.auth.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.auth.application.delegation.RefreshTokenManager;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.auth.application.port.in.LoginUseCase;
import momzzangseven.mztkbe.modules.auth.application.strategy.AuthenticationStrategy;
import momzzangseven.mztkbe.modules.auth.application.strategy.AuthenticationStrategyFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {

  private final AuthenticationStrategyFactory strategyFactory;
  private final JwtTokenProvider jwtTokenProvider;
  private final RefreshTokenManager refreshTokenManager;

  @Override
  public LoginResult execute(LoginCommand command) {
    log.info("Login request received for provider: {}", command.provider());

    // Validate command
    command.validate();

    // Strategy
    AuthenticationStrategy strategy = strategyFactory.getStrategy(command.provider());

    //  Context
    AuthenticationContext context = AuthenticationContext.from(command);

    // Complete authentication flow including user verification and user lookup/registration
    AuthenticatedUser authenticatedUser = strategy.authenticate(context);

    // Create access token
    String accessToken =
        jwtTokenProvider.generateAccessToken(
            authenticatedUser.user().getId(),
            authenticatedUser.user().getEmail(),
            authenticatedUser.user().getRole());

    // Create and save refresh token
    String refreshToken =
        refreshTokenManager.createAndSaveRefreshToken(authenticatedUser.user().getId());

    log.info(
        "Login successful for user: {}, isNewUser: {}",
        authenticatedUser.user().getId(),
        authenticatedUser.isNewUser());

    // Response DTO 생성
    return LoginResult.of(
        accessToken,
        refreshToken,
        jwtTokenProvider.getAccessTokenExpiresIn(),
        jwtTokenProvider.getRefreshTokenExpiresIn(),
        authenticatedUser.isNewUser(),
        authenticatedUser.user());
  }
}
