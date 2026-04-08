package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class Web3SponsorDailyUsageEntityTest {

  @Test
  void onCreate_setsCostColumnsZero_whenNull() {
    Web3SponsorDailyUsageEntity entity =
        Web3SponsorDailyUsageEntity.builder()
            .userId(7L)
            .usageDateKst(LocalDate.of(2026, 3, 1))
            .build();

    entity.onCreate();

    assertThat(entity.getReservedCostWei()).isEqualTo(BigInteger.ZERO);
    assertThat(entity.getConsumedCostWei()).isEqualTo(BigInteger.ZERO);
  }

  @Test
  void onCreate_keepsExistingCosts() {
    Web3SponsorDailyUsageEntity entity =
        Web3SponsorDailyUsageEntity.builder()
            .userId(7L)
            .usageDateKst(LocalDate.of(2026, 3, 1))
            .reservedCostWei(BigInteger.ONE)
            .consumedCostWei(BigInteger.ONE)
            .build();

    entity.onCreate();

    assertThat(entity.getReservedCostWei()).isEqualTo(BigInteger.ONE);
    assertThat(entity.getConsumedCostWei()).isEqualTo(BigInteger.ONE);
  }
}
