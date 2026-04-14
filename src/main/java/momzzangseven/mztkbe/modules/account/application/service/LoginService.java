package momzzangseven.mztkbe.modules.account.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.account.application.dto.IssuedTokens;
import momzzangseven.mztkbe.modules.account.application.dto.LoginCommand;
import momzzangseven.mztkbe.modules.account.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.account.application.port.in.LoginUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserWalletPort;
import momzzangseven.mztkbe.modules.account.application.strategy.AuthenticationStrategy;
import momzzangseven.mztkbe.modules.account.application.strategy.AuthenticationStrategyFactory;
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
            authenticatedUser.userSnapshot().userId(),
            authenticatedUser.userSnapshot().email(),
            authenticatedUser.userSnapshot().role());

    String walletAddress = null;
    try {
      walletAddress =
          loadUserWalletPort
              .loadActiveWalletAddress(authenticatedUser.userSnapshot().userId())
              .orElse(null);
    } catch (Exception e) {
      log.warn(
          "Failed to load wallet address for user {}, skipping: {}",
          authenticatedUser.userSnapshot().userId(),
          e.getMessage());
    }

    log.info(
        "Login successful for user: {}, isNewUser: {}",
        authenticatedUser.userSnapshot().userId(),
        authenticatedUser.isNewUser());

    return LoginResult.of(tokens, authenticatedUser, walletAddress);
  }
}
