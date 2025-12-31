package momzzangseven.mztkbe.modules.auth.application.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  @Override
  @Transactional
  public void execute(LogoutCommand command) {
    String refreshTokenValue = command.getRefreshToken();

    loadRefreshTokenPort
        .findByTokenValueWithLock(refreshTokenValue)
        .ifPresent(this::revokeIfNeeded);
  }

  private void revokeIfNeeded(RefreshToken token) {
    if (token.getRevokedAt() != null) {
      return;
    }

    RefreshToken revoked =
        RefreshToken.builder()
            .id(token.getId())
            .userId(token.getUserId())
            .tokenValue(token.getTokenValue())
            .expiresAt(token.getExpiresAt())
            .revokedAt(LocalDateTime.now())
            .createdAt(token.getCreatedAt())
            .usedAt(token.getUsedAt())
            .build();

    saveRefreshTokenPort.save(revoked);
  }
}
