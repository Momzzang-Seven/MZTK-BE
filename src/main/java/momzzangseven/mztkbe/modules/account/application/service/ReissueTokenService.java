package momzzangseven.mztkbe.modules.account.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.global.error.user.UserWithdrawnException;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.account.application.delegation.RefreshTokenManager;
import momzzangseven.mztkbe.modules.account.application.delegation.RefreshTokenManager.TokenPair;
import momzzangseven.mztkbe.modules.account.application.delegation.RefreshTokenValidator;
import momzzangseven.mztkbe.modules.account.application.dto.ReissueTokenCommand;
import momzzangseven.mztkbe.modules.account.application.dto.ReissueTokenResult;
import momzzangseven.mztkbe.modules.account.application.port.in.ReissueTokenUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.RefreshToken;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReissueTokenService implements ReissueTokenUseCase {

  private final RefreshTokenValidator validator;
  private final RefreshTokenManager refreshTokenManager;
  private final JwtTokenProvider jwtTokenProvider;
  private final LoadUserAccountPort loadUserAccountPort;

  @Override
  public ReissueTokenResult execute(ReissueTokenCommand command) {
    log.info("Token reissue request received");

    command.validate();

    String tokenValue = command.refreshToken();
    validator.validateJwtFormat(tokenValue);

    Long jwtUserId = jwtTokenProvider.getUserIdFromToken(tokenValue);

    UserAccount account =
        loadUserAccountPort
            .findByUserId(jwtUserId)
            .orElseThrow(() -> new UserNotFoundException(jwtUserId));

    if (account.isDeleted()) {
      throw new UserWithdrawnException();
    }

    RefreshToken dbRefreshToken = validator.inspectSecurityFlaw(tokenValue, jwtUserId);
    TokenPair tokenPair = refreshTokenManager.rotateTokens(jwtUserId, dbRefreshToken);

    ReissueTokenResult result =
        ReissueTokenResult.of(
            tokenPair.accessToken(),
            tokenPair.refreshToken(),
            jwtTokenProvider.getAccessTokenExpiresIn(),
            jwtTokenProvider.getRefreshTokenExpiresIn());

    log.info("Token reissue successful: userId={}", jwtUserId);
    return result;
  }
}
