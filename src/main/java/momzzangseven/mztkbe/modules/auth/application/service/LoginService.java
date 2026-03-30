package momzzangseven.mztkbe.modules.auth.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.application.dto.IssuedTokens;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.auth.application.port.in.LoginUseCase;
import momzzangseven.mztkbe.modules.auth.application.port.out.LoadUserWalletPort;
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
  private final AuthTokenIssuer tokenIssuer;
  private final LoadUserWalletPort loadUserWalletPort;

  @Override
  public LoginResult execute(LoginCommand command) {
    log.info("Login request received for provider: {}", command.provider());

    command.validate();

    AuthenticationStrategy strategy = strategyFactory.getStrategy(command.provider());
    AuthenticationContext context = AuthenticationContext.from(command);
    AuthenticatedUser authenticatedUser = strategy.authenticate(context);

    IssuedTokens tokens =
        tokenIssuer.issueTokens(
            authenticatedUser.user().getId(),
            authenticatedUser.user().getEmail(),
            authenticatedUser.user().getRole());

    String walletAddress = null;
    try {
      walletAddress =
          loadUserWalletPort.findActiveWalletAddress(authenticatedUser.user().getId()).orElse(null);
    } catch (Exception e) {
      log.warn(
          "Failed to load wallet address for user {}, skipping: {}",
          authenticatedUser.user().getId(),
          e.getMessage());
    }

    log.info(
        "Login successful for user: {}, isNewUser: {}",
        authenticatedUser.user().getId(),
        authenticatedUser.isNewUser());

    return LoginResult.of(tokens, authenticatedUser, walletAddress);
  }
}
