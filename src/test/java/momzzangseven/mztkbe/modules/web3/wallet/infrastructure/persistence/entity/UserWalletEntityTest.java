package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.web3.wallet.domain.model.WalletStatus;
import org.junit.jupiter.api.Test;

class UserWalletEntityTest {

  @Test
  void onCreate_setsDefaults_whenMissing() {
    UserWalletEntity entity =
        UserWalletEntity.builder().userId(7L).walletAddress("0x" + "a".repeat(40)).build();

    entity.onCreate();

    assertThat(entity.getRegisteredAt()).isNotNull();
    assertThat(entity.getStatus()).isEqualTo(WalletStatus.ACTIVE);
  }

  @Test
  void builder_preservesProvidedStatus() {
    UserWalletEntity entity =
        UserWalletEntity.builder()
            .userId(7L)
            .walletAddress("0x" + "a".repeat(40))
            .status(WalletStatus.BLOCKED)
            .registeredAt(java.time.Instant.parse("2026-03-01T00:00:00Z"))
            .build();

    entity.onCreate();

    assertThat(entity.getStatus()).isEqualTo(WalletStatus.BLOCKED);
  }
}
