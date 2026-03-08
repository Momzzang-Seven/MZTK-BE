package momzzangseven.mztkbe.modules.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExternalDisconnectTask unit test")
class ExternalDisconnectTaskTest {

  @Test
  @DisplayName("markSuccess sets success state and clears retry fields")
  void markSuccess_updatesState() {
    ExternalDisconnectTask task = baseTask();

    ExternalDisconnectTask updated = task.markSuccess(4);

    assertThat(updated.getStatus()).isEqualTo(ExternalDisconnectStatus.SUCCESS);
    assertThat(updated.getAttemptCount()).isEqualTo(4);
    assertThat(updated.getNextAttemptAt()).isNull();
    assertThat(updated.getLastError()).isNull();
    assertThat(updated.getUpdatedAt()).isAfter(task.getUpdatedAt());
  }

  @Test
  @DisplayName("markFailedTerminal keeps attempt count overload and records error")
  void markFailedTerminal_withDefaultAttemptCount_recordsFailure() {
    ExternalDisconnectTask task = baseTask();

    ExternalDisconnectTask failed = task.markFailedTerminal("Timeout");

    assertThat(failed.getStatus()).isEqualTo(ExternalDisconnectStatus.FAILED);
    assertThat(failed.getAttemptCount()).isEqualTo(task.getAttemptCount());
    assertThat(failed.getNextAttemptAt()).isNull();
    assertThat(failed.getLastError()).isEqualTo("Timeout");
  }

  @Test
  @DisplayName("scheduleRetry keeps task pending and updates attempt metadata")
  void scheduleRetry_setsPendingWithNextAttempt() {
    ExternalDisconnectTask task = baseTask();
    LocalDateTime nextAttemptAt = LocalDateTime.of(2026, 3, 1, 1, 0);

    ExternalDisconnectTask retried = task.scheduleRetry(5, nextAttemptAt, "HTTP 500");

    assertThat(retried.getStatus()).isEqualTo(ExternalDisconnectStatus.PENDING);
    assertThat(retried.getAttemptCount()).isEqualTo(5);
    assertThat(retried.getNextAttemptAt()).isEqualTo(nextAttemptAt);
    assertThat(retried.getLastError()).isEqualTo("HTTP 500");
    assertThat(retried.getUpdatedAt()).isAfter(task.getUpdatedAt());
  }

  private ExternalDisconnectTask baseTask() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 2, 20, 8, 0);
    return ExternalDisconnectTask.builder()
        .id(1L)
        .userId(10L)
        .provider(AuthProvider.GOOGLE)
        .providerUserId("google-user")
        .encryptedToken("encrypted")
        .status(ExternalDisconnectStatus.PENDING)
        .attemptCount(3)
        .nextAttemptAt(LocalDateTime.of(2026, 2, 21, 8, 0))
        .lastError("Old error")
        .createdAt(createdAt)
        .updatedAt(createdAt.plusHours(1))
        .build();
  }
}
