package momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Web3TreasuryProvisionAuditEntityTest {

  @Test
  void onCreate_setsCreatedAt_whenNull() {
    Web3TreasuryProvisionAuditEntity entity =
        Web3TreasuryProvisionAuditEntity.builder()
            .operatorId(1L)
            .treasuryAddress("0x" + "a".repeat(40))
            .success(true)
            .build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isNotNull();
  }

  @Test
  void builder_keepsFailureReason() {
    Web3TreasuryProvisionAuditEntity entity =
        Web3TreasuryProvisionAuditEntity.builder()
            .operatorId(1L)
            .treasuryAddress("0x" + "a".repeat(40))
            .success(false)
            .failureReason("INVALID_KEY")
            .build();

    assertThat(entity.getFailureReason()).isEqualTo("INVALID_KEY");
    assertThat(entity.isSuccess()).isFalse();
  }
}
