package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SponsorDailyUsageTest {

  @Test
  void addEstimatedCost_returnsNewAggregateWithAddedValue() {
    SponsorDailyUsage usage =
        SponsorDailyUsage.builder()
            .id(1L)
            .userId(7L)
            .usageDateKst(LocalDate.of(2026, 3, 1))
            .estimatedCostWei(BigInteger.valueOf(100))
            .build();

    SponsorDailyUsage updated = usage.addEstimatedCost(BigInteger.valueOf(250));

    assertThat(updated.getEstimatedCostWei()).isEqualTo(BigInteger.valueOf(350));
    assertThat(usage.getEstimatedCostWei()).isEqualTo(BigInteger.valueOf(100));
  }
}
