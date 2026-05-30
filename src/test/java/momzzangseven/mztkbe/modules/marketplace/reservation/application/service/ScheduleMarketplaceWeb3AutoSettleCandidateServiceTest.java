package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionPhase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminSchedulerExecutionResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleCandidate;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleSkipCategory;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ScheduleMarketplaceWeb3AutoSettleCandidateCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ScheduleMarketplaceWeb3AutoSettleResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceSchedulerAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleMarketplaceWeb3AutoSettleCandidateServiceTest {

  @Mock private ExecuteMarketplaceSchedulerAdminSettlementUseCase executeUseCase;

  @Test
  void mapsProcessedResultToScheduled() {
    given(executeUseCase.execute(any()))
        .willReturn(MarketplaceAdminSchedulerExecutionResult.processed(executionResult()));

    ScheduleMarketplaceWeb3AutoSettleResult result =
        new ScheduleMarketplaceWeb3AutoSettleCandidateService(executeUseCase)
            .execute(new ScheduleMarketplaceWeb3AutoSettleCandidateCommand(candidate(), "run-1"));

    assertThat(result.outcome())
        .isEqualTo(ScheduleMarketplaceWeb3AutoSettleResult.Outcome.SCHEDULED);
    assertThat(result.skipCategory()).isEqualTo(MarketplaceWeb3AutoSettleSkipCategory.NONE);
    assertThat(result.skipReason()).isNull();
  }

  @Test
  void mapsStableSkipCodeWithoutParsingSkipReasonText() {
    given(executeUseCase.execute(any()))
        .willReturn(
            MarketplaceAdminSchedulerExecutionResult.skipped(
                "NON_RETRYABLE_PREPARATION_FAILED", "diagnostic message"));

    ScheduleMarketplaceWeb3AutoSettleResult result =
        new ScheduleMarketplaceWeb3AutoSettleCandidateService(executeUseCase)
            .execute(new ScheduleMarketplaceWeb3AutoSettleCandidateCommand(candidate(), "run-1"));

    assertThat(result.outcome()).isEqualTo(ScheduleMarketplaceWeb3AutoSettleResult.Outcome.SKIPPED);
    assertThat(result.skipCategory())
        .isEqualTo(MarketplaceWeb3AutoSettleSkipCategory.NON_RETRYABLE_PREPARATION_FAILED);
    assertThat(result.skipReason()).isEqualTo("diagnostic message");
  }

  @Test
  void schedulerResultRequiresExclusiveProcessedOrSkippedState() {
    assertThatThrownBy(
            () -> new MarketplaceAdminSchedulerExecutionResult(false, false, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exactly one of processed or skipped");
  }

  @Test
  void delegatesThroughUseCase() {
    given(executeUseCase.execute(any()))
        .willReturn(MarketplaceAdminSchedulerExecutionResult.processed(executionResult()));

    new ScheduleMarketplaceWeb3AutoSettleCandidateService(executeUseCase)
        .execute(new ScheduleMarketplaceWeb3AutoSettleCandidateCommand(candidate(), "run-1"));

    then(executeUseCase).should().execute(any());
  }

  private MarketplaceWeb3AutoSettleCandidate candidate() {
    return new MarketplaceWeb3AutoSettleCandidate(
        1L, "0xorder", LocalDate.of(2026, 5, 29), LocalTime.of(9, 0), 60, null);
  }

  private MarketplaceAdminExecutionResult executionResult() {
    return new MarketplaceAdminExecutionResult(
        1L,
        "MARKETPLACE_ADMIN_SETTLE",
        "0xorder",
        ReservationStatus.AUTO_SETTLED,
        ReservationEscrowStatus.SETTLED,
        new MarketplaceAdminExecutionResult.ExecutionIntent(
            "intent-1", "PREPARED", LocalDateTime.of(2026, 5, 29, 12, 0)),
        new MarketplaceAdminExecutionResult.Execution("SERVER", false, "INTERNAL"),
        MarketplaceAdminExecutionPhase.COMPLETED,
        null,
        null,
        false);
  }
}
