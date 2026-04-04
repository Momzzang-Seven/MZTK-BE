package momzzangseven.mztkbe.modules.account.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.token.RefreshTokenNotFoundException;
import momzzangseven.mztkbe.modules.account.application.delegation.RefreshTokenValidator;
import momzzangseven.mztkbe.modules.account.application.dto.LogoutCommand;
import momzzangseven.mztkbe.modules.account.application.port.in.LogoutUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadRefreshTokenPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveRefreshTokenPort;
import momzzangseven.mztkbe.modules.account.domain.model.RefreshToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutUseCase {

  private final LoadRefreshTokenPort loadRefreshTokenPort;
  private final SaveRefreshTokenPort saveRefreshTokenPort;
  private final RefreshTokenValidator refreshTokenValidator;

  @Override
  @Transactional
  public void execute(LogoutCommand command) {
    String refreshTokenValue = command.refreshToken();
    try {
      refreshTokenValidator.validateJwtFormat(refreshTokenValue);
    } catch (RefreshTokenNotFoundException e) {
      log.debug("Skip logout revoke: invalid refresh token", e);
      return;
    }

    loadRefreshTokenPort
        .findByTokenValueWithLock(refreshTokenValue)
        .ifPresent(this::revokeIfNeeded);
  }

  private void revokeIfNeeded(RefreshToken token) {
    RefreshToken revokedToken = token.revoke();
    saveRefreshTokenPort.save(revokedToken);
  }
}
