package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleScanCursor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleSkipCategory;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RunMarketplaceWeb3AutoSettleBatchResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RunMarketplaceWeb3AutoSettleBatchUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.config.MarketplaceWeb3AutoSettleSchedulerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceWeb3AutoSettleSchedulerTest {

  @Mock private RunMarketplaceWeb3AutoSettleBatchUseCase runBatchUseCase;

  @Test
  void runUsesClockInstantGeneratedRunIdAndBatchUseCase() {
    Clock clock = Clock.fixed(Instant.parse("2026-05-29T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    MarketplaceWeb3AutoSettleSchedulerProperties properties = properties(50, 150, 5, 20);
    given(runBatchUseCase.runBatch(any(), any(), any(), any()))
        .willReturn(
            new RunMarketplaceWeb3AutoSettleBatchResult(
                1,
                1,
                1,
                0,
                0,
                Map.of(),
                MarketplaceWeb3AutoSettleScanCursor.empty(),
                1,
                true,
                false));

    new MarketplaceWeb3AutoSettleScheduler(runBatchUseCase, properties, clock).run();

    then(runBatchUseCase)
        .should()
        .runBatch(
            eq(LocalDateTime.of(2026, 5, 29, 12, 0)),
            eq(properties.loadPolicy()),
            argThat(
                runId ->
                    runId.startsWith("mkt-auto-settle-20260529120000-")
                        && runId.length() == "mkt-auto-settle-20260529120000-".length() + 8),
            eq(MarketplaceWeb3AutoSettleScanCursor.empty()));
  }

  @Test
  void runNowStopsAfterBatchReportsCandidateFailure() {
    MarketplaceWeb3AutoSettleSchedulerProperties properties = properties(50, 150, 5, 20);
    given(runBatchUseCase.runBatch(any(), any(), any(), any()))
        .willReturn(
            new RunMarketplaceWeb3AutoSettleBatchResult(
                20,
                3,
                1,
                1,
                1,
                Map.of(MarketplaceWeb3AutoSettleSkipCategory.LOCK_OR_STATE_RACE, 1),
                cursor(100L, 10, 0),
                1,
                false,
                false));

    var result =
        new MarketplaceWeb3AutoSettleScheduler(runBatchUseCase, properties, fixedClock())
            .runNow(Instant.parse("2026-05-29T03:00:00Z"), "run-fail");

    assertThat(result.batchesRun()).isEqualTo(1);
    assertThat(result.failedCount()).isEqualTo(1);
    assertThat(result.scheduledCount()).isEqualTo(1);
    assertThat(result.skippedCount()).isEqualTo(1);
    then(runBatchUseCase).should(times(1)).runBatch(any(), any(), eq("run-fail"), any());
  }

  @Test
  void runNowContinuesWhenScanLimitIsReachedButBatchStillSchedulesCandidates() {
    MarketplaceWeb3AutoSettleSchedulerProperties properties = properties(50, 150, 5, 20);
    given(runBatchUseCase.runBatch(any(), any(), any(), any()))
        .willReturn(
            new RunMarketplaceWeb3AutoSettleBatchResult(
                150,
                2,
                1,
                1,
                0,
                Map.of(MarketplaceWeb3AutoSettleSkipCategory.LOCK_OR_STATE_RACE, 1),
                cursor(100L, 10, 0),
                5,
                false,
                true),
            new RunMarketplaceWeb3AutoSettleBatchResult(
                10, 1, 1, 0, 0, Map.of(), cursor(101L, 11, 0), 1, true, false));

    var result =
        new MarketplaceWeb3AutoSettleScheduler(runBatchUseCase, properties, fixedClock())
            .runNow(Instant.parse("2026-05-29T03:00:00Z"), "run-continue");

    assertThat(result.batchesRun()).isEqualTo(2);
    assertThat(result.scheduledCount()).isEqualTo(2);
    assertThat(result.skippedCount()).isEqualTo(1);
  }

  @Test
  void runNowContinuesWhenCursorAdvancesEvenWithoutScheduledCandidates() {
    MarketplaceWeb3AutoSettleSchedulerProperties properties = properties(50, 150, 5, 20);
    MarketplaceWeb3AutoSettleScanCursor firstCursor = cursor(100L, 10, 0);
    given(runBatchUseCase.runBatch(any(), any(), any(), any()))
        .willReturn(
            new RunMarketplaceWeb3AutoSettleBatchResult(
                150, 0, 0, 0, 0, Map.of(), firstCursor, 5, false, true),
            new RunMarketplaceWeb3AutoSettleBatchResult(
                10, 1, 1, 0, 0, Map.of(), cursor(101L, 11, 0), 1, true, false));

    var result =
        new MarketplaceWeb3AutoSettleScheduler(runBatchUseCase, properties, fixedClock())
            .runNow(Instant.parse("2026-05-29T03:00:00Z"), "run-scan-progress");

    assertThat(result.batchesRun()).isEqualTo(2);
    assertThat(result.scannedCount()).isEqualTo(160);
    assertThat(result.scheduledCount()).isEqualTo(1);
    then(runBatchUseCase).should(times(2)).runBatch(any(), any(), eq("run-scan-progress"), any());
  }

  @Test
  void runNowCarriesForwardBatchCursorSoNextBatchDoesNotRestartFromHead() {
    MarketplaceWeb3AutoSettleSchedulerProperties properties = properties(50, 150, 5, 20);
    MarketplaceWeb3AutoSettleScanCursor firstCursor = cursor(100L, 10, 0);
    MarketplaceWeb3AutoSettleScanCursor secondCursor = cursor(101L, 11, 0);
    given(runBatchUseCase.runBatch(any(), any(), any(), any()))
        .willReturn(
            new RunMarketplaceWeb3AutoSettleBatchResult(
                150,
                50,
                49,
                1,
                0,
                Map.of(MarketplaceWeb3AutoSettleSkipCategory.LOCK_OR_STATE_RACE, 1),
                firstCursor,
                5,
                false,
                false),
            new RunMarketplaceWeb3AutoSettleBatchResult(
                10, 1, 0, 0, 0, Map.of(), secondCursor, 1, true, false));

    new MarketplaceWeb3AutoSettleScheduler(runBatchUseCase, properties, fixedClock())
        .runNow(Instant.parse("2026-05-29T03:00:00Z"), "run-cursor");

    then(runBatchUseCase)
        .should()
        .runBatch(
            LocalDateTime.of(2026, 5, 29, 12, 0),
            properties.loadPolicy(),
            "run-cursor",
            MarketplaceWeb3AutoSettleScanCursor.empty());
    then(runBatchUseCase)
        .should()
        .runBatch(
            LocalDateTime.of(2026, 5, 29, 12, 0),
            properties.loadPolicy(),
            "run-cursor",
            firstCursor);
  }

  @Test
  void runSwallowsRuntimeException() {
    MarketplaceWeb3AutoSettleSchedulerProperties properties = properties(50, 150, 5, 20);
    given(runBatchUseCase.runBatch(any(), any(), any(), any()))
        .willThrow(new IllegalStateException("boom"));

    assertThatCode(
            () ->
                new MarketplaceWeb3AutoSettleScheduler(runBatchUseCase, properties, fixedClock())
                    .run())
        .doesNotThrowAnyException();
  }

  @Test
  void stableSkipReasonCountsUsesStableEnumNamesInEnumOrder() {
    Map<String, Integer> stable =
        MarketplaceWeb3AutoSettleScheduler.stableSkipReasonCounts(
            Map.of(
                MarketplaceWeb3AutoSettleSkipCategory.UNRESOLVED_EXECUTION_GUARD,
                2,
                MarketplaceWeb3AutoSettleSkipCategory.LOCK_OR_STATE_RACE,
                1));

    assertThat(stable)
        .containsExactly(entry("LOCK_OR_STATE_RACE", 1), entry("UNRESOLVED_EXECUTION_GUARD", 2));
  }

  private static Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-05-29T03:00:00Z"), ZoneId.of("Asia/Seoul"));
  }

  private static MarketplaceWeb3AutoSettleSchedulerProperties properties(
      int batchSize, int scanSize, int maxScanPagesPerBatch, int maxBatchesPerRun) {
    MarketplaceWeb3AutoSettleSchedulerProperties properties =
        new MarketplaceWeb3AutoSettleSchedulerProperties();
    properties.setEnabled(true);
    properties.setBatchSize(batchSize);
    properties.setScanSize(scanSize);
    properties.setMaxScanPagesPerBatch(maxScanPagesPerBatch);
    properties.setMaxBatchesPerRun(maxBatchesPerRun);
    properties.setCron("0 23 * * * *");
    properties.setZone("Asia/Seoul");
    return properties;
  }

  private static MarketplaceWeb3AutoSettleScanCursor cursor(
      Long reservationId, int reservationHour, int reservationMinute) {
    return new MarketplaceWeb3AutoSettleScanCursor(
        LocalDate.of(2026, 5, 27), LocalTime.of(reservationHour, reservationMinute), reservationId);
  }
}
