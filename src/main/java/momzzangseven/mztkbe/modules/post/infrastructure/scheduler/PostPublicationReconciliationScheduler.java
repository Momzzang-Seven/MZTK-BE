package momzzangseven.mztkbe.modules.post.infrastructure.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.post.application.dto.RunPostPublicationReconciliationCommand;
import momzzangseven.mztkbe.modules.post.application.dto.RunPostPublicationReconciliationResult;
import momzzangseven.mztkbe.modules.post.application.port.in.RunPostPublicationReconciliationUseCase;
import momzzangseven.mztkbe.modules.post.infrastructure.config.PostPublicationReconciliationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Driving adapter that runs post publication reconciliation on a schedule. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "post.publication-reconciliation",
    name = "enabled",
    havingValue = "true")
public class PostPublicationReconciliationScheduler {

  private final RunPostPublicationReconciliationUseCase reconciliationUseCase;
  private final PostPublicationReconciliationProperties properties;
  private final AtomicBoolean running = new AtomicBoolean(false);

  @Scheduled(
      cron = "${post.publication-reconciliation.cron}",
      zone = "${post.publication-reconciliation.zone}")
  public void run() {
    if (!running.compareAndSet(false, true)) {
      log.warn("post publication reconciliation skipped: reason=already-running");
      return;
    }

    try {
      runReconciliation();
    } finally {
      running.set(false);
    }
  }

  private void runReconciliation() {
    int batchSize = properties.getBatchSize();
    boolean dryRun = properties.isDryRun();
    int maxBatchesPerRun = properties.getMaxBatchesPerRun();
    Long afterPostId = null;
    RunTotals totals = new RunTotals();

    log.info(
        "post publication reconciliation started: batchSize={}, dryRun={}, maxBatchesPerRun={}",
        batchSize,
        dryRun,
        maxBatchesPerRun);

    try {
      while (true) {
        RunPostPublicationReconciliationResult result =
            reconciliationUseCase.run(
                new RunPostPublicationReconciliationCommand(afterPostId, batchSize, dryRun));
        totals.add(result);
        Long nextAfterPostId = result.lastScannedPostId();

        if (result.needsReviewCount() > 0 || result.staleSkippedCount() > 0) {
          log.warn(
              "post publication reconciliation batch requires attention: afterPostId={}, scanned={}, needsReview={}, needsReviewPostIds={}, staleSkipped={}, staleSkippedPostIds={}, lastPostId={}, dryRun={}",
              afterPostId,
              result.scannedCount(),
              result.needsReviewCount(),
              result.needsReviewPostIds(),
              result.staleSkippedCount(),
              result.staleSkippedPostIds(),
              result.lastScannedPostId(),
              result.dryRun());
        }

        afterPostId = nextAfterPostId;
        if (shouldStop(result, batchSize)) {
          break;
        }

        if (totals.batches >= maxBatchesPerRun) {
          log.warn(
              "post publication reconciliation stopped after max batches: maxBatchesPerRun={}, nextAfterPostId={}, processed={}, dryRun={}",
              maxBatchesPerRun,
              afterPostId,
              totals.scanned,
              dryRun);
          break;
        }
      }
    } catch (Exception e) {
      log.error(
          "post publication reconciliation failed: afterPostId={}, processed={}, dryRun={}",
          afterPostId,
          totals.scanned,
          dryRun,
          e);
      return;
    }

    log.info(
        "post publication reconciliation completed: batches={}, processed={}, unchanged={}, changed={}, changedToPending={}, changedToVisible={}, changedToFailed={}, needsReview={}, staleSkipped={}, lastPostId={}, nextAfterPostId={}, dryRun={}",
        totals.batches,
        totals.scanned,
        totals.unchanged,
        totals.changed(),
        totals.changedToPending,
        totals.changedToVisible,
        totals.changedToFailed,
        totals.needsReview,
        totals.staleSkipped,
        totals.lastScannedPostId,
        afterPostId,
        dryRun);
  }

  private boolean shouldStop(RunPostPublicationReconciliationResult result, int batchSize) {
    return result.scannedCount() == 0
        || result.lastScannedPostId() == null
        || result.scannedCount() < batchSize;
  }

  private static final class RunTotals {
    private int batches;
    private int scanned;
    private int unchanged;
    private int changedToPending;
    private int changedToVisible;
    private int changedToFailed;
    private int needsReview;
    private int staleSkipped;
    private Long lastScannedPostId;

    private void add(RunPostPublicationReconciliationResult result) {
      batches++;
      scanned += result.scannedCount();
      unchanged += result.unchangedCount();
      changedToPending += result.changedToPendingCount();
      changedToVisible += result.changedToVisibleCount();
      changedToFailed += result.changedToFailedCount();
      needsReview += result.needsReviewCount();
      staleSkipped += result.staleSkippedCount();
      lastScannedPostId = result.lastScannedPostId();
    }

    private int changed() {
      return changedToPending + changedToVisible + changedToFailed;
    }
  }
}
