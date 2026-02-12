package momzzangseven.mztkbe.modules.auth.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.auth.UserNotFoundException;
import momzzangseven.mztkbe.global.error.user.UserWithdrawnException;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.auth.application.delegation.RefreshTokenManager;
import momzzangseven.mztkbe.modules.auth.application.delegation.RefreshTokenManager.TokenPair;
import momzzangseven.mztkbe.modules.auth.application.delegation.RefreshTokenValidator;
import momzzangseven.mztkbe.modules.auth.application.dto.ReissueTokenCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.ReissueTokenResult;
import momzzangseven.mztkbe.modules.auth.application.port.in.ReissueTokenUseCase;
import momzzangseven.mztkbe.modules.auth.domain.model.RefreshToken;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for reissuing tokens (Orchestrator).
 *
 * <p>GRASP Pattern: Controller (coordinates other services)
 *
 * <p>Single Responsibility: Orchestrate token reissue workflow
 *
 * <p>Delegates to: - RefreshTokenValidator: Validation logic - RefreshTokenManager: Token
 * generation and rotation
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReissueTokenService implements ReissueTokenUseCase {

  // Collaborators (Delegation)
  private final RefreshTokenValidator validator;
  private final RefreshTokenManager refreshTokenManager;

  // Infrastructure
  private final JwtTokenProvider jwtTokenProvider;
  private final LoadUserPort loadUserPort;

  @Override
  public ReissueTokenResult execute(ReissueTokenCommand command) {
    log.info("Token reissue request received");

    // Step 1: Validate command: validate refresh token exists in the request or not.
    command.validate();

    String tokenValue = command.refreshToken();

    // Step 2: Validate JWT format: validate the token submitted has the legal format or not.
    validator.validateJwtFormat(tokenValue);

    // Step 3: Extract userId(PK) from JWT
    Long jwtUserId = jwtTokenProvider.getUserIdFromToken(tokenValue);

    // Step 3.5: Reject reissue for withdrawn/non-existent users.
    if (loadUserPort.loadUserById(jwtUserId).isEmpty()) {
      if (loadUserPort.loadDeletedUserById(jwtUserId).isPresent()) {
        throw new UserWithdrawnException();
      }
      throw new UserNotFoundException(jwtUserId);
    }

    // Step 4: Inspect the refresh token security flaw. Lock acquisition
    RefreshToken dbRefreshToken = validator.inspectSecurityFlaw(tokenValue, jwtUserId);

    // Step 5: Rotate tokens. Update the DB
    TokenPair tokenPair = refreshTokenManager.rotateTokens(jwtUserId, dbRefreshToken);

    // Step 6: Build result
    ReissueTokenResult result =
        ReissueTokenResult.of(
            tokenPair.accessToken(),
            tokenPair.refreshToken(),
            jwtTokenProvider.getAccessTokenExpiresIn(),
            jwtTokenProvider.getRefreshTokenExpiresIn());

    log.info("Token reissue successful: userId={}", jwtUserId);
    return result; // transition commit, lock release
  }
}
