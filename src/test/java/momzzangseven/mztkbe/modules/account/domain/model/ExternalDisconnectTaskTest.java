package momzzangseven.mztkbe.modules.account.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExternalDisconnectTask unit test")
class ExternalDisconnectTaskTest {

  @Test
  @DisplayName("markSuccess sets success state and clears retry fields")
  void markSuccess_updatesState() {
    ExternalDisconnectTask task = baseTask();
    Instant now = Instant.now();

    ExternalDisconnectTask updated = task.markSuccess(4, now);

    assertThat(updated.getStatus()).isEqualTo(ExternalDisconnectStatus.SUCCESS);
    assertThat(updated.getAttemptCount()).isEqualTo(4);
    assertThat(updated.getNextAttemptAt()).isNull();
    assertThat(updated.getLastError()).isNull();
    assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(task.getUpdatedAt());
  }

  @Test
  @DisplayName("markFailedTerminal records error and preserves attempt count")
  void markFailedTerminal_withAttemptCount_recordsFailure() {
    ExternalDisconnectTask task = baseTask();
    Instant now = Instant.now();

    ExternalDisconnectTask failed = task.markFailedTerminal(task.getAttemptCount(), "Timeout", now);

    assertThat(failed.getStatus()).isEqualTo(ExternalDisconnectStatus.FAILED);
    assertThat(failed.getAttemptCount()).isEqualTo(task.getAttemptCount());
    assertThat(failed.getNextAttemptAt()).isNull();
    assertThat(failed.getLastError()).isEqualTo("Timeout");
  }

  @Test
  @DisplayName("markSuccess uses provided attempt count")
  void markSuccess_usesProvidedAttemptCount() {
    ExternalDisconnectTask task = baseTask();
    Instant now = Instant.now();

    ExternalDisconnectTask updated = task.markSuccess(task.getAttemptCount(), now);

    assertThat(updated.getStatus()).isEqualTo(ExternalDisconnectStatus.SUCCESS);
    assertThat(updated.getAttemptCount()).isEqualTo(task.getAttemptCount());
    assertThat(updated.getNextAttemptAt()).isNull();
    assertThat(updated.getLastError()).isNull();
    assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(task.getUpdatedAt());
  }

  @Test
  @DisplayName("markSuccess returns new object — original is unchanged")
  void markSuccess_immutability_originalUnchanged() {
    ExternalDisconnectTask original = baseTask();
    ExternalDisconnectStatus originalStatus = original.getStatus();
    String originalError = original.getLastError();

    ExternalDisconnectTask updated = original.markSuccess(4, Instant.now());

    assertThat(original.getStatus()).isEqualTo(originalStatus);
    assertThat(original.getLastError()).isEqualTo(originalError);
    assertThat(updated.getStatus()).isEqualTo(ExternalDisconnectStatus.SUCCESS);
  }

  @Test
  @DisplayName("scheduleRetry keeps task pending and updates attempt metadata")
  void scheduleRetry_setsPendingWithNextAttempt() {
    ExternalDisconnectTask task = baseTask();
    Instant now = Instant.now();
    Instant nextAttemptAt = Instant.parse("2026-03-01T01:00:00Z");

    ExternalDisconnectTask retried = task.scheduleRetry(5, nextAttemptAt, "HTTP 500", now);

    assertThat(retried.getStatus()).isEqualTo(ExternalDisconnectStatus.PENDING);
    assertThat(retried.getAttemptCount()).isEqualTo(5);
    assertThat(retried.getNextAttemptAt()).isEqualTo(nextAttemptAt);
    assertThat(retried.getLastError()).isEqualTo("HTTP 500");
    assertThat(retried.getUpdatedAt()).isAfterOrEqualTo(task.getUpdatedAt());
  }

  private ExternalDisconnectTask baseTask() {
    Instant createdAt = Instant.parse("2026-02-20T08:00:00Z");
    return ExternalDisconnectTask.builder()
        .id(1L)
        .userId(10L)
        .provider(AuthProvider.GOOGLE)
        .providerUserId("google-user")
        .encryptedToken("encrypted")
        .status(ExternalDisconnectStatus.PENDING)
        .attemptCount(3)
        .nextAttemptAt(Instant.parse("2026-02-21T08:00:00Z"))
        .lastError("Old error")
        .createdAt(createdAt)
        .updatedAt(createdAt.plus(1, ChronoUnit.HOURS))
        .build();
  }
}
