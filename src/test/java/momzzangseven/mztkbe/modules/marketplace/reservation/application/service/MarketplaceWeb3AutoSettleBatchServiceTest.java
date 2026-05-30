package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleCandidate;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleScanCursor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleSkipCategory;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionCandidateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ScheduleMarketplaceWeb3AutoSettleCandidateCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ScheduleMarketplaceWeb3AutoSettleResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ScheduleMarketplaceWeb3AutoSettleCandidateUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.FindMarketplaceWeb3AutoSettleCandidatesPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCandidatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.MarketplaceWeb3AutoSettlePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceWeb3AutoSettleBatchServiceTest {

  @Mock private FindMarketplaceWeb3AutoSettleCandidatesPort findCandidatesPort;
  @Mock private LoadReservationExecutionStatePort loadReservationExecutionStatePort;
  @Mock private LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort;
  @Mock private ScheduleMarketplaceWeb3AutoSettleCandidateUseCase scheduleCandidateUseCase;

  @Test
  void accumulatesScheduledAndSkippedCounters() {
    MarketplaceWeb3AutoSettleCandidate candidate1 =
        candidate(1L, LocalDateTime.of(2026, 5, 1, 10, 0), null);
    MarketplaceWeb3AutoSettleCandidate candidate2 =
        candidate(2L, LocalDateTime.of(2026, 5, 1, 11, 0), null);
    given(
            findCandidatesPort.findCandidates(
                LocalDateTime.of(2026, 5, 29, 12, 0),
                LocalDateTime.of(2026, 5, 28, 12, 0),
                new MarketplaceWeb3AutoSettleScanCursor(null, null, null),
                10))
        .willReturn(List.of(candidate1, candidate2));
    given(
            scheduleCandidateUseCase.execute(
                new ScheduleMarketplaceWeb3AutoSettleCandidateCommand(candidate1, "run-1")))
        .willReturn(ScheduleMarketplaceWeb3AutoSettleResult.scheduled());
    given(
            scheduleCandidateUseCase.execute(
                new ScheduleMarketplaceWeb3AutoSettleCandidateCommand(candidate2, "run-1")))
        .willReturn(
            ScheduleMarketplaceWeb3AutoSettleResult.skipped(
                MarketplaceWeb3AutoSettleSkipCategory.LOCK_OR_STATE_RACE, "state"));
    given(loadReservationExecutionCandidatePort.findByReservationResource(1L, "0xorder1"))
        .willReturn(List.of());
    given(loadReservationExecutionCandidatePort.findByReservationResource(2L, "0xorder2"))
        .willReturn(List.of());

    var result =
        new MarketplaceWeb3AutoSettleBatchService(
                findCandidatesPort,
                loadReservationExecutionStatePort,
                loadReservationExecutionCandidatePort,
                scheduleCandidateUseCase)
            .runBatch(
                LocalDateTime.of(2026, 5, 29, 12, 0),
                new MarketplaceWeb3AutoSettlePolicy(10, 10, 1, 1),
                "run-1",
                MarketplaceWeb3AutoSettleScanCursor.empty());

    assertThat(result.scannedCount()).isEqualTo(2);
    assertThat(result.eligibleCount()).isEqualTo(2);
    assertThat(result.scheduledCount()).isEqualTo(1);
    assertThat(result.skippedCount()).isEqualTo(1);
    assertThat(result.failedCount()).isZero();
    assertThat(result.skipReasonCounts())
        .containsEntry(MarketplaceWeb3AutoSettleSkipCategory.LOCK_OR_STATE_RACE, 1);
  }

  @Test
  void countsUnexpectedCandidateFailureAndStopsRemainingBatchCandidates() {
    MarketplaceWeb3AutoSettleCandidate candidate1 =
        candidate(1L, LocalDateTime.of(2026, 5, 1, 10, 0), null);
    MarketplaceWeb3AutoSettleCandidate candidate2 =
        candidate(2L, LocalDateTime.of(2026, 5, 1, 11, 0), null);
    MarketplaceWeb3AutoSettleCandidate candidate3 =
        candidate(3L, LocalDateTime.of(2026, 5, 1, 12, 0), null);
    MarketplaceWeb3AutoSettleCandidate candidate4 =
        candidate(4L, LocalDateTime.of(2026, 5, 1, 13, 0), null);
    given(
            findCandidatesPort.findCandidates(
                LocalDateTime.of(2026, 5, 29, 12, 0),
                LocalDateTime.of(2026, 5, 28, 12, 0),
                new MarketplaceWeb3AutoSettleScanCursor(null, null, null),
                10))
        .willReturn(List.of(candidate1, candidate2, candidate3, candidate4));
    given(
            scheduleCandidateUseCase.execute(
                new ScheduleMarketplaceWeb3AutoSettleCandidateCommand(candidate1, "run-1")))
        .willReturn(ScheduleMarketplaceWeb3AutoSettleResult.scheduled());
    given(
            scheduleCandidateUseCase.execute(
                new ScheduleMarketplaceWeb3AutoSettleCandidateCommand(candidate2, "run-1")))
        .willReturn(
            ScheduleMarketplaceWeb3AutoSettleResult.skipped(
                MarketplaceWeb3AutoSettleSkipCategory.LOCK_OR_STATE_RACE, "state"));
    given(
            scheduleCandidateUseCase.execute(
                new ScheduleMarketplaceWeb3AutoSettleCandidateCommand(candidate3, "run-1")))
        .willThrow(new IllegalStateException("boom"));
    given(loadReservationExecutionCandidatePort.findByReservationResource(1L, "0xorder1"))
        .willReturn(List.of());
    given(loadReservationExecutionCandidatePort.findByReservationResource(2L, "0xorder2"))
        .willReturn(List.of());
    given(loadReservationExecutionCandidatePort.findByReservationResource(3L, "0xorder3"))
        .willReturn(List.of());
    given(loadReservationExecutionCandidatePort.findByReservationResource(4L, "0xorder4"))
        .willReturn(List.of());

    var result =
        new MarketplaceWeb3AutoSettleBatchService(
                findCandidatesPort,
                loadReservationExecutionStatePort,
                loadReservationExecutionCandidatePort,
                scheduleCandidateUseCase)
            .runBatch(
                LocalDateTime.of(2026, 5, 29, 12, 0),
                new MarketplaceWeb3AutoSettlePolicy(10, 10, 1, 1),
                "run-1",
                MarketplaceWeb3AutoSettleScanCursor.empty());

    assertThat(result.eligibleCount()).isEqualTo(4);
    assertThat(result.scheduledCount()).isEqualTo(1);
    assertThat(result.skippedCount()).isEqualTo(1);
    assertThat(result.failedCount()).isEqualTo(1);
    assertThat(result.skipReasonCounts())
        .containsEntry(MarketplaceWeb3AutoSettleSkipCategory.LOCK_OR_STATE_RACE, 1);
    then(scheduleCandidateUseCase)
        .should(never())
        .execute(new ScheduleMarketplaceWeb3AutoSettleCandidateCommand(candidate4, "run-1"));
  }

  @Test
  void preservesScanCountersWhenExactFilteringRemovesEveryCandidate() {
    MarketplaceWeb3AutoSettleCandidate tooEarly =
        new MarketplaceWeb3AutoSettleCandidate(
            1L, "0xorder", LocalDate.of(2026, 5, 29), LocalTime.of(15, 0), 60, null);
    given(
            findCandidatesPort.findCandidates(
                LocalDateTime.of(2026, 5, 29, 12, 0),
                LocalDateTime.of(2026, 5, 28, 12, 0),
                new MarketplaceWeb3AutoSettleScanCursor(null, null, null),
                10))
        .willReturn(List.of(tooEarly));

    var result =
        new MarketplaceWeb3AutoSettleBatchService(
                findCandidatesPort,
                loadReservationExecutionStatePort,
                loadReservationExecutionCandidatePort,
                scheduleCandidateUseCase)
            .runBatch(
                LocalDateTime.of(2026, 5, 29, 12, 0),
                new MarketplaceWeb3AutoSettlePolicy(10, 10, 1, 1),
                "run-1",
                MarketplaceWeb3AutoSettleScanCursor.empty());

    assertThat(result.scannedCount()).isEqualTo(1);
    assertThat(result.eligibleCount()).isZero();
    assertThat(result.rawExhausted()).isTrue();
    assertThat(result.scanLimitReached()).isFalse();
  }

  @Test
  void capsEligibleCandidatesAtBatchSize() {
    MarketplaceWeb3AutoSettleCandidate candidate1 =
        candidate(1L, LocalDateTime.of(2026, 5, 1, 10, 0), null);
    MarketplaceWeb3AutoSettleCandidate candidate2 =
        candidate(2L, LocalDateTime.of(2026, 5, 1, 11, 0), null);
    MarketplaceWeb3AutoSettleCandidate candidate3 =
        candidate(3L, LocalDateTime.of(2026, 5, 1, 12, 0), null);
    given(
            findCandidatesPort.findCandidates(
                LocalDateTime.of(2026, 5, 29, 12, 0),
                LocalDateTime.of(2026, 5, 28, 12, 0),
                new MarketplaceWeb3AutoSettleScanCursor(null, null, null),
                10))
        .willReturn(List.of(candidate1, candidate2, candidate3));
    given(
            scheduleCandidateUseCase.execute(
                new ScheduleMarketplaceWeb3AutoSettleCandidateCommand(candidate1, "run-1")))
        .willReturn(ScheduleMarketplaceWeb3AutoSettleResult.scheduled());
    given(
            scheduleCandidateUseCase.execute(
                new ScheduleMarketplaceWeb3AutoSettleCandidateCommand(candidate2, "run-1")))
        .willReturn(ScheduleMarketplaceWeb3AutoSettleResult.scheduled());
    given(loadReservationExecutionCandidatePort.findByReservationResource(1L, "0xorder1"))
        .willReturn(List.of());
    given(loadReservationExecutionCandidatePort.findByReservationResource(2L, "0xorder2"))
        .willReturn(List.of());

    var result =
        new MarketplaceWeb3AutoSettleBatchService(
                findCandidatesPort,
                loadReservationExecutionStatePort,
                loadReservationExecutionCandidatePort,
                scheduleCandidateUseCase)
            .runBatch(
                LocalDateTime.of(2026, 5, 29, 12, 0),
                new MarketplaceWeb3AutoSettlePolicy(2, 10, 1, 1),
                "run-1",
                MarketplaceWeb3AutoSettleScanCursor.empty());

    assertThat(result.eligibleCount()).isEqualTo(2);
    assertThat(result.scheduledCount()).isEqualTo(2);
    assertThat(result.nextCursor())
        .isEqualTo(
            new MarketplaceWeb3AutoSettleScanCursor(
                candidate2.reservationDate(),
                candidate2.reservationTime(),
                candidate2.reservationId()));
  }

  @Test
  void usesCursorPagingUntilExactEligibleBatchIsFilled() {
    LocalDateTime now = LocalDateTime.of(2026, 5, 29, 12, 0);
    MarketplaceWeb3AutoSettleCandidate tooEarly =
        new MarketplaceWeb3AutoSettleCandidate(
            1L, "0xorder1", LocalDate.of(2026, 5, 29), LocalTime.of(15, 0), 60, null);
    MarketplaceWeb3AutoSettleCandidate eligible1 =
        candidate(2L, LocalDateTime.of(2026, 5, 28, 10, 0), null);
    MarketplaceWeb3AutoSettleCandidate eligible2 =
        candidate(3L, LocalDateTime.of(2026, 5, 28, 9, 0), null);
    MarketplaceWeb3AutoSettleScanCursor nextCursor =
        new MarketplaceWeb3AutoSettleScanCursor(
            eligible1.reservationDate(), eligible1.reservationTime(), eligible1.reservationId());
    given(
            findCandidatesPort.findCandidates(
                now,
                now.minusHours(24),
                new MarketplaceWeb3AutoSettleScanCursor(null, null, null),
                2))
        .willReturn(List.of(tooEarly, eligible1));
    given(findCandidatesPort.findCandidates(now, now.minusHours(24), nextCursor, 2))
        .willReturn(List.of(eligible2));
    given(
            scheduleCandidateUseCase.execute(
                new ScheduleMarketplaceWeb3AutoSettleCandidateCommand(eligible1, "run-2")))
        .willReturn(ScheduleMarketplaceWeb3AutoSettleResult.scheduled());
    given(
            scheduleCandidateUseCase.execute(
                new ScheduleMarketplaceWeb3AutoSettleCandidateCommand(eligible2, "run-2")))
        .willReturn(ScheduleMarketplaceWeb3AutoSettleResult.scheduled());
    given(loadReservationExecutionCandidatePort.findByReservationResource(2L, "0xorder2"))
        .willReturn(List.of());
    given(loadReservationExecutionCandidatePort.findByReservationResource(3L, "0xorder3"))
        .willReturn(List.of());

    var result =
        new MarketplaceWeb3AutoSettleBatchService(
                findCandidatesPort,
                loadReservationExecutionStatePort,
                loadReservationExecutionCandidatePort,
                scheduleCandidateUseCase)
            .runBatch(
                now,
                new MarketplaceWeb3AutoSettlePolicy(2, 2, 5, 1),
                "run-2",
                MarketplaceWeb3AutoSettleScanCursor.empty());

    assertThat(result.scannedCount()).isEqualTo(3);
    assertThat(result.eligibleCount()).isEqualTo(2);
    assertThat(result.scheduledCount()).isEqualTo(2);
    assertThat(result.scanPages()).isEqualTo(2);
    assertThat(result.rawExhausted()).isFalse();
    assertThat(result.scanLimitReached()).isFalse();
    then(findCandidatesPort).should().findCandidates(now, now.minusHours(24), nextCursor, 2);
  }

  @Test
  void exactFilteringExcludesRowsWhoseContractDeadlineHasPassed() {
    LocalDateTime now = LocalDateTime.of(2026, 5, 29, 12, 0);
    MarketplaceWeb3AutoSettleCandidate expiredDeadline =
        candidate(4L, LocalDateTime.of(2026, 5, 28, 9, 0), now);
    given(
            findCandidatesPort.findCandidates(
                now,
                now.minusHours(24),
                new MarketplaceWeb3AutoSettleScanCursor(null, null, null),
                10))
        .willReturn(List.of(expiredDeadline));

    var result =
        new MarketplaceWeb3AutoSettleBatchService(
                findCandidatesPort,
                loadReservationExecutionStatePort,
                loadReservationExecutionCandidatePort,
                scheduleCandidateUseCase)
            .runBatch(
                now,
                new MarketplaceWeb3AutoSettlePolicy(10, 10, 1, 1),
                "run-3",
                MarketplaceWeb3AutoSettleScanCursor.empty());

    assertThat(result.scannedCount()).isEqualTo(1);
    assertThat(result.eligibleCount()).isZero();
    assertThat(result.rawExhausted()).isTrue();
    assertThat(result.scanLimitReached()).isFalse();
  }

  @Test
  void exactFilteringExcludesRowsWithBlockingMarketplaceExecutionCandidates() {
    LocalDateTime now = LocalDateTime.of(2026, 5, 29, 12, 0);
    MarketplaceWeb3AutoSettleCandidate blocked =
        candidate(5L, LocalDateTime.of(2026, 5, 28, 8, 0), null);
    given(
            findCandidatesPort.findCandidates(
                now, now.minusHours(24), MarketplaceWeb3AutoSettleScanCursor.empty(), 10))
        .willReturn(List.of(blocked));
    given(loadReservationExecutionCandidatePort.findByReservationResource(5L, "0xorder5"))
        .willReturn(
            List.of(
                new ReservationExecutionCandidateView(
                    "intent-1",
                    "PENDING_ONCHAIN",
                    "MARKETPLACE_ADMIN_SETTLE",
                    1L,
                    null,
                    null,
                    null,
                    null,
                    false)));

    var result =
        new MarketplaceWeb3AutoSettleBatchService(
                findCandidatesPort,
                loadReservationExecutionStatePort,
                loadReservationExecutionCandidatePort,
                scheduleCandidateUseCase)
            .runBatch(
                now,
                new MarketplaceWeb3AutoSettlePolicy(10, 10, 1, 1),
                "run-blocked",
                MarketplaceWeb3AutoSettleScanCursor.empty());

    assertThat(result.scannedCount()).isEqualTo(1);
    assertThat(result.eligibleCount()).isZero();
    assertThat(result.scheduledCount()).isZero();
    then(scheduleCandidateUseCase).shouldHaveNoInteractions();
  }

  private MarketplaceWeb3AutoSettleCandidate candidate(
      Long reservationId, LocalDateTime sessionEndAt, LocalDateTime contractDeadlineAt) {
    LocalDateTime start = sessionEndAt.minusMinutes(60);
    return new MarketplaceWeb3AutoSettleCandidate(
        reservationId,
        "0xorder" + reservationId,
        start.toLocalDate(),
        start.toLocalTime(),
        60,
        contractDeadlineAt);
  }
}
