package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class Web3TreasuryWalletEntityTest {

  @Test
  void onCreate_initializesMissingTimestamps() {
    Web3TreasuryWalletEntity entity = Web3TreasuryWalletEntity.builder().build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isNotNull();
    assertThat(entity.getUpdatedAt()).isNotNull();
  }

  @Test
  void onUpdate_refreshesUpdatedAt() {
    Web3TreasuryWalletEntity entity =
        Web3TreasuryWalletEntity.builder()
            .walletAlias("reward")
            .treasuryAddress("0x" + "a".repeat(40))
            .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
            .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
            .build();

    entity.onUpdate();

    assertThat(entity.getUpdatedAt()).isAfter(LocalDateTime.of(2026, 1, 1, 0, 0));
  }
}
