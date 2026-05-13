package momzzangseven.mztkbe.modules.post.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
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
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostPublicationReconciliationScheduler unit test")
class PostPublicationReconciliationSchedulerTest {

  @Mock private RunPostPublicationReconciliationUseCase reconciliationUseCase;

  private ExecutorService executorService;
  private Logger schedulerLogger;
  private ListAppender<ILoggingEvent> logAppender;

  @AfterEach
  void tearDown() {
    if (executorService != null) {
      executorService.shutdownNow();
    }
    if (schedulerLogger != null && logAppender != null) {
      schedulerLogger.detachAppender(logAppender);
      logAppender.stop();
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
  @DisplayName("attention warning includes needs-review and stale post ids")
  void attentionWarningIncludesPostIds() {
    attachLogAppender();
    PostPublicationReconciliationScheduler scheduler = scheduler(properties(100, true, 10));
    when(reconciliationUseCase.run(any()))
        .thenReturn(resultWithAttention(20, 30L, 2, 1, List.of(101L, 102L), List.of(201L)));

    scheduler.run();

    assertThat(formattedLogMessages())
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("post publication reconciliation batch requires attention")
                    .contains("needsReview=2")
                    .contains("needsReviewPostIds=[101, 102]")
                    .contains("staleSkipped=1")
                    .contains("staleSkippedPostIds=[201]"));
  }

  @Test
  @DisplayName("completed summary log reports accumulated totals")
  void completedSummaryLogReportsAccumulatedTotals() {
    attachLogAppender();
    PostPublicationReconciliationScheduler scheduler = scheduler(properties(100, true, 10));
    when(reconciliationUseCase.run(any()))
        .thenReturn(
            resultWithCounts(100, 1, 2, 3, 4, 1, 2, 10L, List.of(11L), List.of(21L, 22L)),
            resultWithCounts(
                20, 5, 6, 7, 8, 3, 4, 30L, List.of(31L, 32L, 33L), List.of(41L, 42L, 43L, 44L)));

    scheduler.run();

    assertThat(formattedLogMessages())
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("post publication reconciliation completed")
                    .contains("processed=120")
                    .contains("unchanged=6")
                    .contains("changed=30")
                    .contains("changedToPending=8")
                    .contains("changedToVisible=10")
                    .contains("changedToFailed=12")
                    .contains("needsReview=4")
                    .contains("staleSkipped=6"));
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
        scannedCount, 0, 0, 0, 0, 0, 0, lastScannedPostId, true, List.of(), List.of());
  }

  private RunPostPublicationReconciliationResult resultWithAttention(
      int scannedCount, Long lastScannedPostId, int needsReviewCount, int staleSkippedCount) {
    return resultWithAttention(
        scannedCount, lastScannedPostId, needsReviewCount, staleSkippedCount, List.of(), List.of());
  }

  private RunPostPublicationReconciliationResult resultWithAttention(
      int scannedCount,
      Long lastScannedPostId,
      int needsReviewCount,
      int staleSkippedCount,
      List<Long> needsReviewPostIds,
      List<Long> staleSkippedPostIds) {
    return new RunPostPublicationReconciliationResult(
        scannedCount,
        0,
        0,
        0,
        0,
        needsReviewCount,
        staleSkippedCount,
        lastScannedPostId,
        true,
        needsReviewPostIds,
        staleSkippedPostIds);
  }

  private RunPostPublicationReconciliationResult resultWithCounts(
      int scannedCount,
      int unchangedCount,
      int changedToPendingCount,
      int changedToVisibleCount,
      int changedToFailedCount,
      int needsReviewCount,
      int staleSkippedCount,
      Long lastScannedPostId,
      List<Long> needsReviewPostIds,
      List<Long> staleSkippedPostIds) {
    return new RunPostPublicationReconciliationResult(
        scannedCount,
        unchangedCount,
        changedToPendingCount,
        changedToVisibleCount,
        changedToFailedCount,
        needsReviewCount,
        staleSkippedCount,
        lastScannedPostId,
        true,
        needsReviewPostIds,
        staleSkippedPostIds);
  }

  private void attachLogAppender() {
    schedulerLogger =
        (Logger) LoggerFactory.getLogger(PostPublicationReconciliationScheduler.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    schedulerLogger.addAppender(logAppender);
  }

  private List<String> formattedLogMessages() {
    return logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  @Configuration
  @Import(PostPublicationReconciliationScheduler.class)
  @EnableConfigurationProperties(PostPublicationReconciliationProperties.class)
  private static class SchedulerConditionTestConfig {}
}
