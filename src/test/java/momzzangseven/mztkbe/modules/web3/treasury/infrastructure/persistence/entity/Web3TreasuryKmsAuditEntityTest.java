package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Web3TreasuryKmsAuditEntity} — covers [M-166] (PrePersist + builder).
 *
 * <p>Database-level CHECK constraints (action_type enum + wallet_address regex) are validated by
 * {@code MigrationValidationTest} against the real PostgreSQL integration profile DB, so are not
 * re-asserted here.
 */
@DisplayName("Web3TreasuryKmsAuditEntity 단위 테스트")
class Web3TreasuryKmsAuditEntityTest {

  @Test
  @DisplayName("[M-166a] @PrePersist — createdAt이 null이면 onCreate가 채워줌")
  void onCreate_setsCreatedAt_whenNull() {
    Web3TreasuryKmsAuditEntity entity =
        Web3TreasuryKmsAuditEntity.builder()
            .operatorId(1L)
            .walletAlias("reward-treasury")
            .kmsKeyId("kms-id")
            .actionType("KMS_DISABLE")
            .success(true)
            .build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("[M-166b] @PrePersist — createdAt이 이미 설정되어 있으면 덮어쓰지 않음")
  void onCreate_doesNotOverwriteExistingCreatedAt() {
    LocalDateTime preset = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
    Web3TreasuryKmsAuditEntity entity =
        Web3TreasuryKmsAuditEntity.builder()
            .operatorId(1L)
            .walletAlias("reward-treasury")
            .kmsKeyId("kms-id")
            .actionType("KMS_DISABLE")
            .success(true)
            .createdAt(preset)
            .build();

    entity.onCreate();

    assertThat(entity.getCreatedAt()).isEqualTo(preset);
  }

  @Test
  @DisplayName("[M-166c] builder — failureReason 필드 유지 (success=false 분기)")
  void builder_keepsFailureReasonOnFailureBranch() {
    Web3TreasuryKmsAuditEntity entity =
        Web3TreasuryKmsAuditEntity.builder()
            .operatorId(1L)
            .walletAlias("reward-treasury")
            .kmsKeyId("kms-id")
            .walletAddress("0x" + "a".repeat(40))
            .actionType("KMS_CREATE_ALIAS")
            .success(false)
            .failureReason("KmsAliasAlreadyExistsException")
            .build();

    assertThat(entity.getFailureReason()).isEqualTo("KmsAliasAlreadyExistsException");
    assertThat(entity.isSuccess()).isFalse();
    assertThat(entity.getActionType()).isEqualTo("KMS_CREATE_ALIAS");
    assertThat(entity.getWalletAddress()).isEqualTo("0x" + "a".repeat(40));
  }
}
