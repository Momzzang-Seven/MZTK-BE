package momzzangseven.mztkbe.modules.web3.transfer.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SponsorDailyUsageTest {

  @Test
  void consume_returnsNewAggregateWithAddedValue() {
    SponsorDailyUsage usage =
        SponsorDailyUsage.builder()
            .id(1L)
            .userId(7L)
            .usageDateKst(LocalDate.of(2026, 3, 1))
            .reservedCostWei(BigInteger.valueOf(100))
            .consumedCostWei(BigInteger.valueOf(200))
            .build();

    SponsorDailyUsage updated = usage.consume(BigInteger.valueOf(250));

    assertThat(updated.getConsumedCostWei()).isEqualTo(BigInteger.valueOf(450));
    assertThat(updated.getReservedCostWei()).isEqualTo(BigInteger.valueOf(100));
    assertThat(usage.getConsumedCostWei()).isEqualTo(BigInteger.valueOf(200));
  }
}
