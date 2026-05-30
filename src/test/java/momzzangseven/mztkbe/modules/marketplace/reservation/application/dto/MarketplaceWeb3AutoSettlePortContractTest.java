package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.MarketplaceWeb3AutoSettlePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarketplaceWeb3AutoSettlePortContractTest {

  @Test
  @DisplayName("candidate는 session end 시각을 계산한다")
  void candidateComputesSessionEndAt() {
    MarketplaceWeb3AutoSettleCandidate candidate =
        new MarketplaceWeb3AutoSettleCandidate(
            1L, "0xorder", LocalDate.of(2026, 5, 29), LocalTime.of(9, 30), 90, null);

    assertThat(candidate.sessionEndAt()).isEqualTo(LocalDateTime.of(2026, 5, 29, 11, 0));
  }

  @Test
  @DisplayName("policy는 scan-size가 batch-size보다 작으면 거부한다")
  void policyRejectsScanSizeSmallerThanBatchSize() {
    assertThatThrownBy(() -> new MarketplaceWeb3AutoSettlePolicy(50, 49, 5, 20))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("scanSize");
  }

  @Test
  @DisplayName("scan cursor는 전부 null이거나 전부 채워져야 한다")
  void cursorRequiresAllOrNothing() {
    assertThatThrownBy(() -> new MarketplaceWeb3AutoSettleScanCursor(LocalDate.now(), null, 1L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("scan cursor");
  }

  @Test
  @DisplayName("scheduled result는 skipCategory NONE과 null reason만 허용한다")
  void scheduledResultRequiresNoneSkipCategory() {
    assertThat(ScheduleMarketplaceWeb3AutoSettleResult.scheduled().skipCategory())
        .isEqualTo(MarketplaceWeb3AutoSettleSkipCategory.NONE);
    assertThatThrownBy(
            () ->
                new ScheduleMarketplaceWeb3AutoSettleResult(
                    ScheduleMarketplaceWeb3AutoSettleResult.Outcome.SCHEDULED,
                    MarketplaceWeb3AutoSettleSkipCategory.LOCK_OR_STATE_RACE,
                    "reason"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("batch result와 run result는 immutable skipReasonCounts를 보장한다")
  void resultMapsAreImmutable() {
    RunMarketplaceWeb3AutoSettleBatchResult batchResult =
        new RunMarketplaceWeb3AutoSettleBatchResult(
            1,
            1,
            0,
            1,
            0,
            Map.of(MarketplaceWeb3AutoSettleSkipCategory.LOCK_OR_STATE_RACE, 1),
            MarketplaceWeb3AutoSettleScanCursor.empty(),
            1,
            true,
            false);
    RunMarketplaceWeb3AutoSettleResult runResult =
        new RunMarketplaceWeb3AutoSettleResult(
            1,
            1,
            1,
            0,
            1,
            0,
            Map.of(MarketplaceWeb3AutoSettleSkipCategory.LOCK_OR_STATE_RACE, 1),
            1,
            true,
            false);

    assertThatThrownBy(
            () ->
                batchResult
                    .skipReasonCounts()
                    .put(MarketplaceWeb3AutoSettleSkipCategory.UNKNOWN_STATE_SKIP, 1))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(
            () ->
                runResult
                    .skipReasonCounts()
                    .put(MarketplaceWeb3AutoSettleSkipCategory.UNKNOWN_STATE_SKIP, 1))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
