package momzzangseven.mztkbe.modules.account.application.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.account.application.port.out.ExternalDisconnectTaskPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadExternalDisconnectPolicyPort;
import momzzangseven.mztkbe.modules.account.domain.model.ExternalDisconnectStatus;
import momzzangseven.mztkbe.modules.account.domain.model.ExternalDisconnectTask;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
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
  private final LoadExternalDisconnectPolicyPort policyPort;
  private final ExternalDisconnectTaskPort externalDisconnectTaskPort;

  /**
   * Best-effort external disconnect during withdrawal.
   *
   * <p>Failures are captured as {@link ExternalDisconnectTask} for retry; withdrawal must still
   * complete.
   */
  public void disconnectOnWithdrawal(Long userId, UserAccount account) {
    AuthProvider provider = account.getProvider();
    if (provider == null || provider == AuthProvider.LOCAL) {
      return;
    }

    if (provider != AuthProvider.KAKAO && provider != AuthProvider.GOOGLE) {
      log.warn("Unsupported social provider for external disconnect: provider={}", provider);
      return;
    }

    try {
      executor.disconnect(provider, account.getProviderUserId(), account.getGoogleRefreshToken());
    } catch (Exception e) {
      log.warn(
          "External disconnect failed; enqueue retry task: userId={}, provider={}, error={}",
          userId,
          provider,
          e.getMessage(),
          e);
      enqueueRetryTask(userId, account, provider, e);
    }
  }

  private void enqueueRetryTask(
      Long userId, UserAccount account, AuthProvider provider, Exception error) {
    LocalDateTime now = LocalDateTime.now();
    ExternalDisconnectTask task =
        ExternalDisconnectTask.builder()
            .userId(userId)
            .provider(provider)
            .providerUserId(account.getProviderUserId())
            .encryptedToken(
                provider == AuthProvider.GOOGLE ? account.getGoogleRefreshToken() : null)
            .status(ExternalDisconnectStatus.PENDING)
            .attemptCount(1)
            .nextAttemptAt(now.plus(policyPort.getInitialBackoff(), ChronoUnit.MILLIS))
            .lastError(error.getClass().getSimpleName() + ": " + error.getMessage())
            .createdAt(now)
            .updatedAt(now)
            .build();

    externalDisconnectTaskPort.save(task);
  }
}
