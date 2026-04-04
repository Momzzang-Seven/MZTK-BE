package momzzangseven.mztkbe.modules.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import momzzangseven.mztkbe.modules.account.application.port.out.ExternalDisconnectTaskPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadExternalDisconnectPolicyPort;
import momzzangseven.mztkbe.modules.account.domain.model.ExternalDisconnectStatus;
import momzzangseven.mztkbe.modules.account.domain.model.ExternalDisconnectTask;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalDisconnectRetryService unit test")
class ExternalDisconnectRetryServiceTest {

  @Mock private ExternalDisconnectTaskPort externalDisconnectTaskPort;
  @Mock private ExternalDisconnectExecutor executor;
  @Mock private LoadExternalDisconnectPolicyPort policyPort;

  private ExternalDisconnectRetryService service;

  @BeforeEach
  void setUp() {
    service = new ExternalDisconnectRetryService(externalDisconnectTaskPort, policyPort, executor);
  }

  @Test
  @DisplayName("runBatch returns picked count and skips non-pending task")
  void runBatch_skipsNonPendingTask() {
    when(policyPort.getBatchSize()).thenReturn(10);
    ExternalDisconnectTask nonPending = baseTask(ExternalDisconnectStatus.SUCCESS, 2);
    when(externalDisconnectTaskPort.findDueTasks(any(LocalDateTime.class), eq(10)))
        .thenReturn(List.of(nonPending));

    int processed = service.runBatch();

    assertThat(processed).isEqualTo(1);
    verify(executor, never()).disconnect(any(), any(), any());
    verify(externalDisconnectTaskPort, never()).save(any(ExternalDisconnectTask.class));
  }

  @Test
  @DisplayName("runBatch marks task success when disconnect succeeds")
  void runBatch_onSuccess_marksSuccess() {
    when(policyPort.getBatchSize()).thenReturn(10);
    ExternalDisconnectTask pending = baseTask(ExternalDisconnectStatus.PENDING, 1);
    when(externalDisconnectTaskPort.findDueTasks(any(LocalDateTime.class), eq(10)))
        .thenReturn(List.of(pending));

    service.runBatch();

    ArgumentCaptor<ExternalDisconnectTask> savedCaptor =
        ArgumentCaptor.forClass(ExternalDisconnectTask.class);
    verify(externalDisconnectTaskPort).save(savedCaptor.capture());

    ExternalDisconnectTask saved = savedCaptor.getValue();
    assertThat(saved.getStatus()).isEqualTo(ExternalDisconnectStatus.SUCCESS);
    assertThat(saved.getAttemptCount()).isEqualTo(2);
    assertThat(saved.getNextAttemptAt()).isNull();
    assertThat(saved.getLastError()).isNull();
    verify(executor).disconnect(AuthProvider.GOOGLE, "provider-user", "encrypted-token");
  }

  @Test
  @DisplayName("runBatch marks terminal failure when max attempts reached")
  void runBatch_onFailureAtMaxAttempts_marksFailed() {
    when(policyPort.getBatchSize()).thenReturn(10);
    when(policyPort.getMaxAttempts()).thenReturn(3);
    ExternalDisconnectTask pending = baseTask(ExternalDisconnectStatus.PENDING, 2);
    when(externalDisconnectTaskPort.findDueTasks(any(LocalDateTime.class), eq(10)))
        .thenReturn(List.of(pending));
    RuntimeException boom = new RuntimeException("boom");
    org.mockito.Mockito.doThrow(boom)
        .when(executor)
        .disconnect(AuthProvider.GOOGLE, "provider-user", "encrypted-token");

    service.runBatch();

    ArgumentCaptor<ExternalDisconnectTask> savedCaptor =
        ArgumentCaptor.forClass(ExternalDisconnectTask.class);
    verify(externalDisconnectTaskPort).save(savedCaptor.capture());

    ExternalDisconnectTask saved = savedCaptor.getValue();
    assertThat(saved.getStatus()).isEqualTo(ExternalDisconnectStatus.FAILED);
    assertThat(saved.getAttemptCount()).isEqualTo(3);
    assertThat(saved.getNextAttemptAt()).isNull();
    assertThat(saved.getLastError()).isEqualTo("RuntimeException: boom");
  }

  @Test
  @DisplayName("runBatch schedules retry with clamped backoff when attempts remain")
  void runBatch_onFailureBeforeMaxAttempts_schedulesRetry() {
    when(policyPort.getBatchSize()).thenReturn(10);
    when(policyPort.getMaxAttempts()).thenReturn(20);
    when(policyPort.getInitialBackoff()).thenReturn(1_000L);
    when(policyPort.getMaxBackoff()).thenReturn(5_000L);

    ExternalDisconnectTask pending = baseTask(ExternalDisconnectStatus.PENDING, 10);
    when(externalDisconnectTaskPort.findDueTasks(any(LocalDateTime.class), eq(10)))
        .thenReturn(List.of(pending));
    org.mockito.Mockito.doThrow(new IllegalStateException("downstream"))
        .when(executor)
        .disconnect(AuthProvider.GOOGLE, "provider-user", "encrypted-token");

    LocalDateTime before = LocalDateTime.now();
    service.runBatch();

    ArgumentCaptor<ExternalDisconnectTask> savedCaptor =
        ArgumentCaptor.forClass(ExternalDisconnectTask.class);
    verify(externalDisconnectTaskPort).save(savedCaptor.capture());

    ExternalDisconnectTask saved = savedCaptor.getValue();
    assertThat(saved.getStatus()).isEqualTo(ExternalDisconnectStatus.PENDING);
    assertThat(saved.getAttemptCount()).isEqualTo(11);
    assertThat(saved.getLastError()).isEqualTo("IllegalStateException: downstream");
    long scheduledDelayMillis = ChronoUnit.MILLIS.between(before, saved.getNextAttemptAt());
    assertThat(scheduledDelayMillis).isBetween(4_500L, 7_000L);
  }

  private ExternalDisconnectTask baseTask(ExternalDisconnectStatus status, int attemptCount) {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 10, 0);
    return ExternalDisconnectTask.builder()
        .id(1L)
        .userId(99L)
        .provider(AuthProvider.GOOGLE)
        .providerUserId("provider-user")
        .encryptedToken("encrypted-token")
        .status(status)
        .attemptCount(attemptCount)
        .nextAttemptAt(now)
        .lastError("previous")
        .createdAt(now.minusDays(1))
        .updatedAt(now.minusHours(1))
        .build();
  }
}
