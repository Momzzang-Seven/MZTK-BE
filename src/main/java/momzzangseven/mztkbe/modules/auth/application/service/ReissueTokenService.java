package momzzangseven.mztkbe.modules.auth.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.auth.application.delegation.RefreshTokenManager;
import momzzangseven.mztkbe.modules.auth.application.delegation.RefreshTokenManager.TokenPair;
import momzzangseven.mztkbe.modules.auth.application.delegation.RefreshTokenValidator;
import momzzangseven.mztkbe.modules.auth.application.dto.ReissueTokenCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.ReissueTokenResult;
import momzzangseven.mztkbe.modules.auth.application.port.in.ReissueTokenUseCase;
import momzzangseven.mztkbe.modules.auth.domain.model.RefreshToken;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for reissuing tokens (Orchestrator).
 *
 * <p>GRASP Pattern: Controller (coordinates other services)
 *
 * <p>Single Responsibility: Orchestrate token reissue workflow
 *
 * <p>Delegates to: - RefreshTokenValidator: Validation logic - TokenRotationService: Token
 * generation and rotation
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReissueTokenService implements ReissueTokenUseCase {

  // Collaborators (Delegation)
  private final RefreshTokenValidator validator;
  private final RefreshTokenManager tokenManager;

  // Infrastructure
  private final JwtTokenProvider jwtTokenProvider;
  private final LoadUserPort loadUserPort;

  @Value("${jwt.access-token-expiration}")
  private long accessTokenExpiration;

  @Override
  public ReissueTokenResult execute(ReissueTokenCommand command) {
    log.info("Token reissue request received");

    // Step 1: Validate command
    command.validate();
    String tokenValue = command.refreshToken();

    // Step 2: Validate JWT format (delegated)
    validator.validateJwtFormat(tokenValue);

    // Step 3: Extract userId(PK) from JWT
    Long jwtUserId = jwtTokenProvider.getUserIdFromToken(tokenValue);

    // Step 4: Load token from DB (delegated)
    RefreshToken dbRefreshToken = validator.loadAndValidate(tokenValue);

    // Step 5: Validate userId consistency (delegated)
    validator.validateUserIdConsistency(jwtUserId, dbRefreshToken);

    // Step 6: Validate domain rules (delegated)
    validator.validateDomainRules(dbRefreshToken);

    // Step 7: Check for token reuse (delegated)
    tokenManager.checkTokenReuse(dbRefreshToken, 5);

    // Step 8: Mark token as used (delegated)
    tokenManager.markTokenUsed(dbRefreshToken);

    // Step 9: Load user information
    User user =
        loadUserPort
            .loadUserById(jwtUserId)
            .orElseThrow(() -> new UserNotFoundException(jwtUserId));

    // Step 10: Rotate tokens (delegated)
    TokenPair tokenPair = tokenManager.rotateTokens(user, dbRefreshToken);

    // Step 11: Build result
    ReissueTokenResult result =
        ReissueTokenResult.of(
            tokenPair.accessToken(), tokenPair.refreshToken(), accessTokenExpiration);

    log.info("Token reissue successful: userId={}", jwtUserId);
    return result;
  }
}
