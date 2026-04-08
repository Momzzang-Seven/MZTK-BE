package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Web3NonceStateEntityTest {

  @Test
  void onCreate_setsDefaults() {
    Web3NonceStateEntity entity =
        Web3NonceStateEntity.builder().fromAddress("0x" + "a".repeat(40)).build();

    entity.onCreate();

    assertThat(entity.getNextNonce()).isEqualTo(0L);
  }

  @Test
  void onCreate_keepsExistingNonce() {
    Web3NonceStateEntity entity =
        Web3NonceStateEntity.builder().fromAddress("0x" + "a".repeat(40)).nextNonce(3L).build();

    entity.onCreate();

    assertThat(entity.getNextNonce()).isEqualTo(3L);
  }
}
