package momzzangseven.mztkbe.modules.account.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.global.error.user.UserBlockedException;
import momzzangseven.mztkbe.global.error.user.UserUnverifiedException;
import momzzangseven.mztkbe.global.error.user.UserWithdrawnException;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.account.application.delegation.RefreshTokenManager;
import momzzangseven.mztkbe.modules.account.application.delegation.RefreshTokenManager.TokenPair;
import momzzangseven.mztkbe.modules.account.application.delegation.RefreshTokenValidator;
import momzzangseven.mztkbe.modules.account.application.dto.ReissueTokenCommand;
import momzzangseven.mztkbe.modules.account.application.dto.ReissueTokenResult;
import momzzangseven.mztkbe.modules.account.application.port.in.CheckAccountStatusUseCase;
import momzzangseven.mztkbe.modules.account.application.port.in.ReissueTokenUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.CheckAdminRefreshSubjectPort;
import momzzangseven.mztkbe.modules.account.domain.model.RefreshToken;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
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
  private final CheckAccountStatusUseCase checkAccountStatusUseCase;
  private final CheckAdminRefreshSubjectPort checkAdminRefreshSubjectPort;

  @Override
  public ReissueTokenResult execute(ReissueTokenCommand command) {
    log.info("Token reissue request received");

    command.validate();

    String tokenValue = command.refreshToken();
    validator.validateJwtFormat(tokenValue);

    Long jwtUserId = jwtTokenProvider.getUserIdFromToken(tokenValue);

    validateRefreshSubject(jwtUserId);

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

  private void validateRefreshSubject(Long userId) {
    Optional<AccountStatus> status = checkAccountStatusUseCase.findStatus(userId);
    if (status.isEmpty()) {
      if (!checkAdminRefreshSubjectPort.isActiveAdmin(userId)) {
        throw new UserNotFoundException(userId);
      }
      return;
    }
    // Reject ALL non-ACTIVE statuses so refresh-token reissue matches the auth hot path
    // (JwtAuthenticationFilter denies anything that is not ACTIVE). An exhaustive switch with no
    // default means a future AccountStatus value is a compile error here, not a silent bypass.
    AccountStatus value = status.get();
    switch (value) {
      case ACTIVE -> {
        // proceed with reissue
      }
      case DELETED -> throw new UserWithdrawnException();
      case BLOCKED -> throw new UserBlockedException();
      case UNVERIFIED -> throw new UserUnverifiedException();
    }
  }
}
