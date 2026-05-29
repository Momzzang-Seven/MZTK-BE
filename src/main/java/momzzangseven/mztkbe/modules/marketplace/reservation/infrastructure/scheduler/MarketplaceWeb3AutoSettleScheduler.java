package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.scheduler;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.config.ConditionalOnMarketplaceAdminEnabled;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleRunId;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleScanCursor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleSkipCategory;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RunMarketplaceWeb3AutoSettleBatchResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RunMarketplaceWeb3AutoSettleResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RunMarketplaceWeb3AutoSettleBatchUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.MarketplaceWeb3AutoSettlePolicy;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.config.MarketplaceWeb3AutoSettleSchedulerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnMarketplaceAdminEnabled
@ConditionalOnProperty(
    prefix = "web3.marketplace.admin.auto-settle",
    name = "enabled",
    havingValue = "true")
public class MarketplaceWeb3AutoSettleScheduler {

  private final RunMarketplaceWeb3AutoSettleBatchUseCase runBatchUseCase;
  private final MarketplaceWeb3AutoSettleSchedulerProperties properties;
  private final Clock appClock;

  @Scheduled(
      cron = "#{@marketplaceWeb3AutoSettleSchedulerProperties.cron}",
      zone = "#{@marketplaceWeb3AutoSettleSchedulerProperties.zone}")
  public void run() {
    long startedAt = System.nanoTime();
    Instant now = Instant.now(appClock);
    String schedulerRunId = MarketplaceWeb3AutoSettleRunId.generate(appClock).value();
    try {
      RunMarketplaceWeb3AutoSettleResult result = runNow(now, schedulerRunId);
      log.info(
          "marketplace web3 auto-settle scheduler completed: schedulerRunId={}, batchesRun={}, scannedCount={}, eligibleCount={}, scheduledCount={}, skippedCount={}, failedCount={}, scanPages={}, rawExhausted={}, scanLimitReached={}, skipReasonCounts={}, elapsedMs={}",
          schedulerRunId,
          result.batchesRun(),
          result.scannedCount(),
          result.eligibleCount(),
          result.scheduledCount(),
          result.skippedCount(),
          result.failedCount(),
          result.scanPages(),
          result.rawExhausted(),
          result.scanLimitReached(),
          stableSkipReasonCounts(result.skipReasonCounts()),
          (System.nanoTime() - startedAt) / 1_000_000L);
    } catch (RuntimeException e) {
      log.error(
          "marketplace web3 auto-settle scheduler failed: schedulerRunId={}", schedulerRunId, e);
    }
  }

  public RunMarketplaceWeb3AutoSettleResult runNow(Instant now, String schedulerRunId) {
    MarketplaceWeb3AutoSettlePolicy policy = properties.loadPolicy();
    LocalDateTime localNow = LocalDateTime.ofInstant(now, appClock.getZone());
    int batchesRun = 0;
    int scannedCount = 0;
    int eligibleCount = 0;
    int scheduledCount = 0;
    int skippedCount = 0;
    int failedCount = 0;
    int scanPages = 0;
    boolean rawExhausted = false;
    boolean scanLimitReached = false;
    Map<MarketplaceWeb3AutoSettleSkipCategory, Integer> skipReasonCounts =
        new EnumMap<>(MarketplaceWeb3AutoSettleSkipCategory.class);
    MarketplaceWeb3AutoSettleScanCursor cursor = MarketplaceWeb3AutoSettleScanCursor.empty();

    for (int batchNo = 0; batchNo < policy.maxBatchesPerRun(); batchNo++) {
      MarketplaceWeb3AutoSettleScanCursor batchStartCursor = cursor;
      RunMarketplaceWeb3AutoSettleBatchResult batch =
          runBatchUseCase.runBatch(localNow, policy, schedulerRunId, cursor);
      batchesRun++;
      scannedCount += batch.scannedCount();
      eligibleCount += batch.eligibleCount();
      scheduledCount += batch.scheduledCount();
      skippedCount += batch.skippedCount();
      failedCount += batch.failedCount();
      scanPages += batch.scanPages();
      rawExhausted = batch.rawExhausted();
      scanLimitReached = batch.scanLimitReached();
      cursor = batch.nextCursor();
      merge(skipReasonCounts, batch.skipReasonCounts());

      boolean batchAdvancedCursor = !cursor.equals(batchStartCursor);
      if (batch.failedCount() > 0
          || (batch.eligibleCount() < policy.batchSize() && batch.rawExhausted())
          || (batch.scheduledCount() == 0 && !batchAdvancedCursor)) {
        break;
      }
    }

    return new RunMarketplaceWeb3AutoSettleResult(
        batchesRun,
        scannedCount,
        eligibleCount,
        scheduledCount,
        skippedCount,
        failedCount,
        skipReasonCounts,
        scanPages,
        rawExhausted,
        scanLimitReached);
  }

  static Map<String, Integer> stableSkipReasonCounts(
      Map<MarketplaceWeb3AutoSettleSkipCategory, Integer> skipReasonCounts) {
    Map<String, Integer> stable = new LinkedHashMap<>();
    for (MarketplaceWeb3AutoSettleSkipCategory category :
        MarketplaceWeb3AutoSettleSkipCategory.values()) {
      Integer count = skipReasonCounts.get(category);
      if (count != null && count > 0) {
        stable.put(category.name(), count);
      }
    }
    return stable;
  }

  private void merge(
      Map<MarketplaceWeb3AutoSettleSkipCategory, Integer> totals,
      Map<MarketplaceWeb3AutoSettleSkipCategory, Integer> increments) {
    increments.forEach((key, value) -> totals.merge(key, value, Integer::sum));
  }
}
