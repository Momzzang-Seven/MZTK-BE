package momzzangseven.mztkbe.modules.auth.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.token.RefreshTokenNotFoundException;
import momzzangseven.mztkbe.modules.auth.application.delegation.RefreshTokenValidator;
import momzzangseven.mztkbe.modules.auth.application.dto.LogoutCommand;
import momzzangseven.mztkbe.modules.auth.application.port.in.LogoutUseCase;
import momzzangseven.mztkbe.modules.auth.application.port.out.LoadRefreshTokenPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.SaveRefreshTokenPort;
import momzzangseven.mztkbe.modules.auth.domain.model.RefreshToken;
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
    String refreshTokenValue = command.getRefreshToken();
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
    token.revoke();
    saveRefreshTokenPort.save(token);
  }
}
