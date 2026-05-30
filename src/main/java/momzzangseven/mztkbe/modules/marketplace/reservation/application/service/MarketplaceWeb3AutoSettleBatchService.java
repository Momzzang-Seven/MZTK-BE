package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleCandidate;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleScanCursor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleSkipCategory;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RunMarketplaceWeb3AutoSettleBatchResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ScheduleMarketplaceWeb3AutoSettleCandidateCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ScheduleMarketplaceWeb3AutoSettleResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RunMarketplaceWeb3AutoSettleBatchUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ScheduleMarketplaceWeb3AutoSettleCandidateUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.FindMarketplaceWeb3AutoSettleCandidatesPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCandidatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.MarketplaceWeb3AutoSettlePolicy;

@Slf4j
public class MarketplaceWeb3AutoSettleBatchService
    implements RunMarketplaceWeb3AutoSettleBatchUseCase {

  private final FindMarketplaceWeb3AutoSettleCandidatesPort findCandidatesPort;
  private final ReservationExecutionCandidateGuard executionCandidateGuard;
  private final ScheduleMarketplaceWeb3AutoSettleCandidateUseCase scheduleCandidateUseCase;

  public MarketplaceWeb3AutoSettleBatchService(
      FindMarketplaceWeb3AutoSettleCandidatesPort findCandidatesPort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort,
      ScheduleMarketplaceWeb3AutoSettleCandidateUseCase scheduleCandidateUseCase) {
    this.findCandidatesPort = findCandidatesPort;
    this.executionCandidateGuard =
        new ReservationExecutionCandidateGuard(
            loadReservationExecutionStatePort, loadReservationExecutionCandidatePort);
    this.scheduleCandidateUseCase = scheduleCandidateUseCase;
  }

  @Override
  public RunMarketplaceWeb3AutoSettleBatchResult runBatch(
      LocalDateTime now,
      MarketplaceWeb3AutoSettlePolicy policy,
      String schedulerRunId,
      MarketplaceWeb3AutoSettleScanCursor startCursor) {
    Objects.requireNonNull(startCursor, "startCursor");
    LocalDateTime settleCutoff = now.minusHours(24);
    MarketplaceWeb3AutoSettleScanCursor cursor = startCursor;
    int scannedCount = 0;
    int scanPages = 0;
    boolean rawExhausted = false;
    boolean batchFilled = false;
    List<MarketplaceWeb3AutoSettleCandidate> eligibleCandidates = new ArrayList<>();

    while (scanPages < policy.maxScanPagesPerBatch() && !batchFilled) {
      List<MarketplaceWeb3AutoSettleCandidate> rawRows =
          findCandidatesPort.findCandidates(now, settleCutoff, cursor, policy.scanSize());
      scanPages++;
      scannedCount += rawRows.size();
      if (rawRows.isEmpty()) {
        rawExhausted = true;
        break;
      }

      for (MarketplaceWeb3AutoSettleCandidate rawRow : rawRows) {
        cursor =
            new MarketplaceWeb3AutoSettleScanCursor(
                rawRow.reservationDate(), rawRow.reservationTime(), rawRow.reservationId());
        if (isExactEligible(rawRow, now) && !hasBlockingMarketplaceExecution(rawRow)) {
          eligibleCandidates.add(rawRow);
          if (eligibleCandidates.size() == policy.batchSize()) {
            batchFilled = true;
            break;
          }
        }
      }

      if (batchFilled) {
        break;
      }
      if (rawRows.size() < policy.scanSize()) {
        rawExhausted = true;
        break;
      }
    }

    boolean scanLimitReached = !rawExhausted && !batchFilled;
    int eligibleCount = eligibleCandidates.size();
    if (eligibleCount == 0) {
      return RunMarketplaceWeb3AutoSettleBatchResult.empty(
          scannedCount, cursor, scanPages, rawExhausted, scanLimitReached);
    }

    int scheduledCount = 0;
    int skippedCount = 0;
    int failedCount = 0;
    Map<MarketplaceWeb3AutoSettleSkipCategory, Integer> skipReasonCounts =
        new EnumMap<>(MarketplaceWeb3AutoSettleSkipCategory.class);

    for (MarketplaceWeb3AutoSettleCandidate candidate : eligibleCandidates) {
      try {
        ScheduleMarketplaceWeb3AutoSettleResult result =
            scheduleCandidateUseCase.execute(
                new ScheduleMarketplaceWeb3AutoSettleCandidateCommand(candidate, schedulerRunId));
        if (result.outcome() == ScheduleMarketplaceWeb3AutoSettleResult.Outcome.SCHEDULED) {
          scheduledCount++;
          continue;
        }
        skippedCount++;
        skipReasonCounts.merge(result.skipCategory(), 1, Integer::sum);
      } catch (RuntimeException exception) {
        failedCount++;
        log.error(
            "marketplace web3 auto-settle candidate scheduling failed: schedulerRunId={}, reservationId={}, orderKey={}",
            schedulerRunId,
            candidate.reservationId(),
            candidate.orderKey(),
            exception);
        break;
      }
    }

    return new RunMarketplaceWeb3AutoSettleBatchResult(
        scannedCount,
        eligibleCount,
        scheduledCount,
        skippedCount,
        failedCount,
        skipReasonCounts,
        cursor,
        scanPages,
        rawExhausted,
        scanLimitReached);
  }

  private boolean isExactEligible(MarketplaceWeb3AutoSettleCandidate candidate, LocalDateTime now) {
    return !candidate.sessionEndAt().plusHours(24).isAfter(now)
        && (candidate.contractDeadlineAt() == null || candidate.contractDeadlineAt().isAfter(now));
  }

  private boolean hasBlockingMarketplaceExecution(MarketplaceWeb3AutoSettleCandidate candidate) {
    return executionCandidateGuard.hasBlockingExecutionForMarketplaceResource(
        candidate.reservationId(), candidate.orderKey());
  }
}
