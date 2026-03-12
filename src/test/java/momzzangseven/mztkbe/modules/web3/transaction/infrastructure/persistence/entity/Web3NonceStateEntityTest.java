package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class Web3NonceStateEntityTest {

  @Test
  void onCreate_setsDefaults() {
    Web3NonceStateEntity entity =
        Web3NonceStateEntity.builder().fromAddress("0x" + "a".repeat(40)).build();

    entity.onCreate();

    assertThat(entity.getNextNonce()).isEqualTo(0L);
    assertThat(entity.getUpdatedAt()).isNotNull();
  }

  @Test
  void onUpdate_refreshesUpdatedAt() {
    Web3NonceStateEntity entity =
        Web3NonceStateEntity.builder()
            .fromAddress("0x" + "a".repeat(40))
            .nextNonce(3L)
            .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
            .build();

    entity.onUpdate();

    assertThat(entity.getUpdatedAt()).isAfter(LocalDateTime.of(2026, 1, 1, 0, 0));
  }
}
