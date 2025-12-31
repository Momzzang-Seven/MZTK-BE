package momzzangseven.mztkbe.modules.user.application.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.config.WithdrawalExternalDisconnectProperties;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveExternalDisconnectTaskPort;
import momzzangseven.mztkbe.modules.user.domain.model.ExternalDisconnectStatus;
import momzzangseven.mztkbe.modules.user.domain.model.ExternalDisconnectTask;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.stereotype.Service;

/**
 * Handles social provider disconnection (Kakao unlink / Google revoke) during withdrawal.
 *
 * <p>External calls must not block/rollback withdrawal; failures are captured as retryable tasks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalDisconnectService {

  private final ExternalDisconnectExecutor executor;
  private final WithdrawalExternalDisconnectProperties props;
  private final SaveExternalDisconnectTaskPort saveExternalDisconnectTaskPort;

  /**
   * Best-effort external disconnect during withdrawal.
   *
   * <p>Failures are captured as {@link ExternalDisconnectTask} for retry; withdrawal must still
   * complete.
   */
  public void disconnectOnWithdrawal(User user) {
    AuthProvider provider = user.getAuthProvider();
    if (provider == null || provider == AuthProvider.LOCAL) {
      return;
    }

    if (provider != AuthProvider.KAKAO && provider != AuthProvider.GOOGLE) {
      log.warn("Unsupported social provider for external disconnect: provider={}", provider);
      return;
    }

    try {
      executor.disconnect(provider, user.getProviderUserId(), user.getGoogleRefreshToken());
    } catch (Exception e) {
      log.warn(
          "External disconnect failed; enqueue retry task: userId={}, provider={}, error={}",
          user.getId(),
          provider,
          e.getMessage(),
          e);
      enqueueRetryTask(user, provider, e);
    }
  }

  private void enqueueRetryTask(User user, AuthProvider provider, Exception error) {
    LocalDateTime now = LocalDateTime.now();
    ExternalDisconnectTask task =
        ExternalDisconnectTask.builder()
            .userId(user.getId())
            .provider(provider)
            .providerUserId(user.getProviderUserId())
            .encryptedToken(provider == AuthProvider.GOOGLE ? user.getGoogleRefreshToken() : null)
            .status(ExternalDisconnectStatus.PENDING)
            .attemptCount(1)
            .nextAttemptAt(now.plus(props.getInitialBackoff(), ChronoUnit.MILLIS))
            .lastError(error.getClass().getSimpleName() + ": " + error.getMessage())
            .createdAt(now)
            .updatedAt(now)
            .build();

    saveExternalDisconnectTaskPort.save(task);
  }
}
