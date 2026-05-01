package momzzangseven.mztkbe.integration.e2e.web3.treasury;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * E2E migration invariants for {@code web3_treasury_wallets} (V059..V061) and {@code
 * web3_treasury_kms_audits} (V062).
 *
 * <p>Cases [E-102]..[E-105] from {@code
 * docs/test/refactor-MOM-384-reward-eip-1559-change-to-kms.md}. Uses PostgreSQL-only constructs
 * ({@code pg_constraint}, {@code NOW()}, {@code repeat()}) so it must run under the {@code
 * integration} profile.
 *
 * <p>{@code web3_treasury_wallets} is excluded from {@link
 * momzzangseven.mztkbe.integration.e2e.support.DatabaseCleaner}; this class explicitly deletes the
 * test wallet rows in {@link #cleanInsertedRows()}.
 */
@DisplayName("[E2E] Treasury wallets / KMS audits migration invariants (V059..V062)")
class Web3TreasuryWalletsMigrationE2ETest extends E2ETestBase {

  private static final String LEGACY_ALIAS = "legacy-row";
  private static final String KMS_ONLY_ALIAS = "kms-only";

  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanInsertedRows() {
    jdbcTemplate.update(
        "DELETE FROM web3_treasury_wallets WHERE wallet_alias IN (?, ?)",
        LEGACY_ALIAS,
        KMS_ONLY_ALIAS);
  }

  @Test
  @DisplayName("[E-102] V060 — kms_key_id 컬럼은 nullable, 레거시 INSERT (kms_key_id 미지정) 가 성공")
  void v060_kmsKeyIdColumn_isNullable_legacyInsertSucceeds() {
    String legacyAddress = "0x" + "1".repeat(40);

    assertThatCode(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO web3_treasury_wallets"
                        + " (wallet_alias, treasury_address, status, key_origin, created_at,"
                        + " updated_at)"
                        + " VALUES (?, ?, 'ACTIVE', 'IMPORTED', NOW(), NOW())",
                    LEGACY_ALIAS,
                    legacyAddress))
        .doesNotThrowAnyException();

    String kmsKeyId =
        jdbcTemplate.queryForObject(
            "SELECT kms_key_id FROM web3_treasury_wallets WHERE wallet_alias = ?",
            String.class,
            LEGACY_ALIAS);
    assertThat(kmsKeyId).isNull();
  }

  @Test
  @DisplayName("[E-103] V061 — slot_pair CHECK DROP 후 KMS-only row INSERT 가 성공")
  void v061_slotPairCheckDropped_kmsOnlyRowInsertSucceeds() {
    String kmsAddress = "0xdead000000000000000000000000000000000001";

    assertThatCode(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO web3_treasury_wallets"
                        + " (wallet_alias, treasury_address, kms_key_id, status, key_origin,"
                        + " treasury_private_key_encrypted, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'ACTIVE', 'IMPORTED', NULL, NOW(), NOW())",
                    KMS_ONLY_ALIAS,
                    kmsAddress,
                    "alias/kms-only"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("[E-104] V061 — uk_web3_treasury_wallets_treasury_address 명명된 unique 제약이 존재")
  void v061_namedUniqueConstraintOnTreasuryAddress_exists() {
    Integer namedCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_constraint"
                + " WHERE conrelid = 'web3_treasury_wallets'::regclass"
                + " AND contype = 'u'"
                + " AND conname = 'uk_web3_treasury_wallets_treasury_address'",
            Integer.class);
    assertThat(namedCount).isEqualTo(1);

    Integer legacyCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_constraint"
                + " WHERE conrelid = 'web3_treasury_wallets'::regclass"
                + " AND conname = 'web3_treasury_keys_treasury_address_key'",
            Integer.class);
    assertThat(legacyCount).isZero();
  }

  @Test
  @DisplayName("[E-105] V062 — web3_treasury_kms_audits CHECK 제약 정의 + 미허용 action_type 거부")
  void v062_kmsAuditsCheckConstraints_existAndRejectInvalidAction() {
    List<String> constraintNames =
        jdbcTemplate.queryForList(
            "SELECT conname FROM pg_constraint"
                + " WHERE conrelid = 'web3_treasury_kms_audits'::regclass"
                + " AND conname IN ('ck_web3_treasury_kms_audits_action',"
                + " 'ck_web3_treasury_kms_audits_address_format')"
                + " ORDER BY conname",
            String.class);
    assertThat(constraintNames)
        .containsExactly(
            "ck_web3_treasury_kms_audits_action", "ck_web3_treasury_kms_audits_address_format");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO web3_treasury_kms_audits"
                        + " (wallet_alias, action_type, success, created_at)"
                        + " VALUES (?, 'UNKNOWN', true, NOW())",
                    "reward-treasury"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
