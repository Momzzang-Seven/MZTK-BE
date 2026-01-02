package momzzangseven.mztkbe.modules.user.domain.model;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;

/** A retryable task to disconnect a social provider account during withdrawal. */
@Getter
@Builder(toBuilder = true)
public class ExternalDisconnectTask {
  private Long id;
  private Long userId;
  private AuthProvider provider;
  private String providerUserId;
  private String encryptedToken; // e.g., Google refresh token (encrypted)
  private ExternalDisconnectStatus status;
  private int attemptCount;
  private LocalDateTime nextAttemptAt;
  private String lastError;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** Mark this task as successfully disconnected. */
  public ExternalDisconnectTask markSuccess() {
    return markSuccess(this.attemptCount);
  }

  /** Mark this task as successfully disconnected with the updated attempt count. */
  public ExternalDisconnectTask markSuccess(int attemptCount) {
    LocalDateTime now = LocalDateTime.now();
    return this.toBuilder()
        .status(ExternalDisconnectStatus.SUCCESS)
        .attemptCount(attemptCount)
        .nextAttemptAt(null)
        .lastError(null)
        .updatedAt(now)
        .build();
  }

  /** Mark this task as terminally failed (no more retries). */
  public ExternalDisconnectTask markFailedTerminal(String error) {
    return markFailedTerminal(this.attemptCount, error);
  }

  /** Mark this task as terminally failed (no more retries) with the updated attempt count. */
  public ExternalDisconnectTask markFailedTerminal(int attemptCount, String error) {
    LocalDateTime now = LocalDateTime.now();
    return this.toBuilder()
        .status(ExternalDisconnectStatus.FAILED)
        .attemptCount(attemptCount)
        .nextAttemptAt(null)
        .lastError(error)
        .updatedAt(now)
        .build();
  }

  /**
   * Schedule next retry for this task.
   *
   * @param nextAttemptCount total number of attempts after this failure
   * @param nextAttemptAt next scheduled time
   * @param error last error message
   * @return updated task
   */
  public ExternalDisconnectTask scheduleRetry(
      int nextAttemptCount, LocalDateTime nextAttemptAt, String error) {
    LocalDateTime now = LocalDateTime.now();
    return this.toBuilder()
        .status(ExternalDisconnectStatus.PENDING)
        .attemptCount(nextAttemptCount)
        .nextAttemptAt(nextAttemptAt)
        .lastError(error)
        .updatedAt(now)
        .build();
  }
}
