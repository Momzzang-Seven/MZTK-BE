package momzzangseven.mztkbe.modules.post.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import momzzangseven.mztkbe.modules.post.application.dto.RunPostPublicationReconciliationCommand;
import momzzangseven.mztkbe.modules.post.application.dto.RunPostPublicationReconciliationResult;
import momzzangseven.mztkbe.modules.post.application.port.in.RunPostPublicationReconciliationUseCase;
import momzzangseven.mztkbe.modules.post.infrastructure.config.PostPublicationReconciliationProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostPublicationReconciliationScheduler unit test")
class PostPublicationReconciliationSchedulerTest {

  @Mock private RunPostPublicationReconciliationUseCase reconciliationUseCase;

  private ExecutorService executorService;

  @AfterEach
  void tearDown() {
    if (executorService != null) {
      executorService.shutdownNow();
    }
  }

  @Test
  @DisplayName("enabled=false does not create scheduler bean")
  void enabledFalseDoesNotCreateSchedulerBean() {
    contextRunner()
        .withPropertyValues("post.publication-reconciliation.enabled=false")
        .run(
            context ->
                assertThat(context).doesNotHaveBean(PostPublicationReconciliationScheduler.class));
  }

  @Test
  @DisplayName("enabled=true creates scheduler bean")
  void enabledTrueCreatesSchedulerBean() {
    contextRunner()
        .withPropertyValues("post.publication-reconciliation.enabled=true")
        .run(
            context ->
                assertThat(context).hasSingleBean(PostPublicationReconciliationScheduler.class));
  }

  @Test
  @DisplayName("safe operational defaults are dry-run and disabled")
  void propertiesDefaultToSafeOperationalValues() {
    PostPublicationReconciliationProperties properties =
        new PostPublicationReconciliationProperties();

    assertThat(properties.isEnabled()).isFalse();
    assertThat(properties.isDryRun()).isTrue();
    assertThat(properties.getBatchSize()).isEqualTo(100);
    assertThat(properties.getMaxBatchesPerRun()).isEqualTo(10);
  }

  @Test
  @DisplayName("run passes dryRun, batchSize, and cursor to use case")
  void runPassesDryRunBatchSizeAndCursor() {
    PostPublicationReconciliationScheduler scheduler = scheduler(properties(100, true, 10));
    when(reconciliationUseCase.run(any())).thenReturn(result(100, 10L), result(20, 30L));

    scheduler.run();

    ArgumentCaptor<RunPostPublicationReconciliationCommand> commandCaptor =
        ArgumentCaptor.forClass(RunPostPublicationReconciliationCommand.class);
    verify(reconciliationUseCase, times(2)).run(commandCaptor.capture());
    assertThat(commandCaptor.getAllValues())
        .extracting(RunPostPublicationReconciliationCommand::afterPostId)
        .containsExactly(null, 10L);
    assertThat(commandCaptor.getAllValues())
        .extracting(RunPostPublicationReconciliationCommand::batchSize)
        .containsExactly(100, 100);
    assertThat(commandCaptor.getAllValues())
        .extracting(RunPostPublicationReconciliationCommand::dryRun)
        .containsExactly(true, true);
  }

  @Test
  @DisplayName("run stops when scanned count is less than batch size")
  void runStopsWhenScannedCountIsLessThanBatchSize() {
    PostPublicationReconciliationScheduler scheduler = scheduler(properties(100, true, 10));
    when(reconciliationUseCase.run(any())).thenReturn(result(99, 99L));

    scheduler.run();

    verify(reconciliationUseCase, times(1)).run(any());
  }

  @Test
  @DisplayName("run stops when scanned count is zero")
  void runStopsWhenScannedCountIsZero() {
    PostPublicationReconciliationScheduler scheduler = scheduler(properties(100, true, 10));
    when(reconciliationUseCase.run(any())).thenReturn(result(0, null));

    scheduler.run();

    verify(reconciliationUseCase, times(1)).run(any());
  }

  @Test
  @DisplayName("run stops when lastScannedPostId is null")
  void runStopsWhenLastScannedPostIdIsNull() {
    PostPublicationReconciliationScheduler scheduler = scheduler(properties(100, true, 10));
    when(reconciliationUseCase.run(any())).thenReturn(result(100, null));

    scheduler.run();

    verify(reconciliationUseCase, times(1)).run(any());
  }

  @Test
  @DisplayName("run stops at maxBatchesPerRun")
  void runStopsAtMaxBatchesPerRun() {
    PostPublicationReconciliationScheduler scheduler = scheduler(properties(100, true, 2));
    when(reconciliationUseCase.run(any()))
        .thenReturn(result(100, 10L), result(100, 20L), result(100, 30L));

    scheduler.run();

    verify(reconciliationUseCase, times(2)).run(any());
  }

  @Test
  @DisplayName("needs-review and stale rows do not stop cursor progress")
  void needsReviewAndStaleRowsDoNotStopCursorProgress() {
    PostPublicationReconciliationScheduler scheduler = scheduler(properties(100, true, 10));
    when(reconciliationUseCase.run(any()))
        .thenReturn(resultWithAttention(100, 10L, 1, 1), result(20, 30L));

    scheduler.run();

    ArgumentCaptor<RunPostPublicationReconciliationCommand> commandCaptor =
        ArgumentCaptor.forClass(RunPostPublicationReconciliationCommand.class);
    verify(reconciliationUseCase, times(2)).run(commandCaptor.capture());
    assertThat(commandCaptor.getAllValues())
        .extracting(RunPostPublicationReconciliationCommand::afterPostId)
        .containsExactly(null, 10L);
  }

  @Test
  @DisplayName("use case failure is swallowed by scheduler")
  void useCaseFailureIsSwallowed() {
    PostPublicationReconciliationScheduler scheduler = scheduler(properties(100, true, 10));
    when(reconciliationUseCase.run(any())).thenThrow(new IllegalStateException("boom"));

    assertThatCode(scheduler::run).doesNotThrowAnyException();
    verify(reconciliationUseCase, times(1)).run(any());
  }

  @Test
  @DisplayName("overlapping run is skipped by running guard")
  void overlappingRunIsSkippedByRunningGuard() throws Exception {
    PostPublicationReconciliationScheduler scheduler = scheduler(properties(100, true, 10));
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(reconciliationUseCase.run(any()))
        .thenAnswer(
            invocation -> {
              started.countDown();
              if (!release.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("first scheduler run was not released");
              }
              return result(0, null);
            });
    executorService = Executors.newSingleThreadExecutor();

    var firstRun = executorService.submit(scheduler::run);
    assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

    scheduler.run();

    verify(reconciliationUseCase, times(1)).run(any());
    release.countDown();
    firstRun.get(2, TimeUnit.SECONDS);
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(SchedulerConditionTestConfig.class)
        .withBean(
            RunPostPublicationReconciliationUseCase.class,
            () -> mock(RunPostPublicationReconciliationUseCase.class));
  }

  private PostPublicationReconciliationScheduler scheduler(
      PostPublicationReconciliationProperties properties) {
    return new PostPublicationReconciliationScheduler(reconciliationUseCase, properties);
  }

  private PostPublicationReconciliationProperties properties(
      int batchSize, boolean dryRun, int maxBatchesPerRun) {
    PostPublicationReconciliationProperties properties =
        new PostPublicationReconciliationProperties();
    properties.setEnabled(true);
    properties.setBatchSize(batchSize);
    properties.setDryRun(dryRun);
    properties.setMaxBatchesPerRun(maxBatchesPerRun);
    return properties;
  }

  private RunPostPublicationReconciliationResult result(int scannedCount, Long lastScannedPostId) {
    return new RunPostPublicationReconciliationResult(
        scannedCount, 0, 0, 0, 0, 0, 0, lastScannedPostId, true);
  }

  private RunPostPublicationReconciliationResult resultWithAttention(
      int scannedCount, Long lastScannedPostId, int needsReviewCount, int staleSkippedCount) {
    return new RunPostPublicationReconciliationResult(
        scannedCount, 0, 0, 0, 0, needsReviewCount, staleSkippedCount, lastScannedPostId, true);
  }

  @Configuration
  @Import(PostPublicationReconciliationScheduler.class)
  @EnableConfigurationProperties(PostPublicationReconciliationProperties.class)
  private static class SchedulerConditionTestConfig {}
}
