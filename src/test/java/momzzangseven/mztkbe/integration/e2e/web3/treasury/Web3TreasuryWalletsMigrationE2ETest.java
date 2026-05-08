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
 * E2E migration invariants for {@code web3_treasury_wallets} (KMS column additions through the
 * KMS-finalize cleanup migration) and {@code web3_treasury_kms_audits}.
 *
 * <p>Cases [E-102]..[E-105] from {@code
 * docs/test/refactor-MOM-384-reward-eip-1559-change-to-kms.md}. [E-106]..[E-109] cover the
 * KMS-finalize cleanup migration's CHECK constraints (status / key_origin / kms_key_id_required)
 * and the migration body's idempotency on replay. Uses PostgreSQL-only constructs ({@code
 * pg_constraint}, {@code NOW()}, {@code repeat()}) so it must run under the {@code integration}
 * profile.
 *
 * <p>{@code web3_treasury_wallets} is excluded from {@link
 * momzzangseven.mztkbe.integration.e2e.support.DatabaseCleaner}; this class explicitly deletes the
 * test wallet rows in {@link #cleanInsertedRows()}.
 */
@DisplayName("[E2E] Treasury wallets / KMS audits migration invariants")
class Web3TreasuryWalletsMigrationE2ETest extends E2ETestBase {

  private static final String LEGACY_ALIAS = "legacy-row";
  private static final String KMS_ONLY_ALIAS = "kms-only";
  private static final String CHECK_REJECT_ALIAS_STATUS = "check-reject-status";
  private static final String CHECK_REJECT_ALIAS_KEY_ORIGIN = "check-reject-key-origin";
  private static final String CHECK_REJECT_ALIAS_KMS_NULL = "check-reject-kms-null";

  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanInsertedRows() {
    jdbcTemplate.update(
        "DELETE FROM web3_treasury_wallets WHERE wallet_alias IN (?, ?, ?, ?, ?)",
        LEGACY_ALIAS,
        KMS_ONLY_ALIAS,
        CHECK_REJECT_ALIAS_STATUS,
        CHECK_REJECT_ALIAS_KEY_ORIGIN,
        CHECK_REJECT_ALIAS_KMS_NULL);
  }

  @Test
  @DisplayName("[E-102] KMS-finalize cleanup — kms_key_id NOT NULL 강제, kms_key_id 미지정 INSERT 가 거부")
  void kmsFinalizeCleanup_kmsKeyIdColumn_isNotNull_legacyInsertRejected() {
    String legacyAddress = "0x" + "1".repeat(40);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO web3_treasury_wallets"
                        + " (wallet_alias, treasury_address, status, key_origin, created_at,"
                        + " updated_at)"
                        + " VALUES (?, ?, 'ACTIVE', 'IMPORTED', NOW(), NOW())",
                    LEGACY_ALIAS,
                    legacyAddress))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("[E-103] KMS-finalize cleanup — KMS-only row INSERT 가 성공 (legacy 컬럼 DROP 후)")
  void kmsFinalizeCleanup_kmsOnlyRowInsertSucceeds() {
    String kmsAddress = "0xdead000000000000000000000000000000000001";

    assertThatCode(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO web3_treasury_wallets"
                        + " (wallet_alias, treasury_address, kms_key_id, status, key_origin,"
                        + " created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'ACTIVE', 'IMPORTED', NOW(), NOW())",
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

  @Test
  @DisplayName("[E-106] KMS-finalize cleanup — status 가 허용 enum 외 값이면 거부")
  void kmsFinalizeCleanup_status_mustBeInAllowedEnum_rejectsUnknown() {
    String addr = "0x" + "2".repeat(40);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO web3_treasury_wallets"
                        + " (wallet_alias, treasury_address, kms_key_id, status, key_origin,"
                        + " created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'UNKNOWN', 'IMPORTED', NOW(), NOW())",
                    CHECK_REJECT_ALIAS_STATUS,
                    addr,
                    "alias/check-reject-status"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("[E-107] KMS-finalize cleanup — key_origin 이 'IMPORTED' 외 값이면 거부")
  void kmsFinalizeCleanup_keyOrigin_mustBeImported_rejectsGenerated() {
    String addr = "0x" + "3".repeat(40);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO web3_treasury_wallets"
                        + " (wallet_alias, treasury_address, kms_key_id, status, key_origin,"
                        + " created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'ACTIVE', 'GENERATED', NOW(), NOW())",
                    CHECK_REJECT_ALIAS_KEY_ORIGIN,
                    addr,
                    "alias/check-reject-key-origin"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("[E-108] KMS-finalize cleanup — kms_key_id NULL row 는 거부 (NOT NULL + CHECK 이중 가드)")
  void kmsFinalizeCleanup_kmsKeyIdRequiredCheck_rejectsKmsKeyIdNull() {
    String addr = "0x" + "4".repeat(40);

    // NOT NULL 과 ck_web3_treasury_wallets_kms_key_id_required 가 동일한 위반을 잡는다 (defense-in-depth).
    // PG 는 NOT NULL 위반을 먼저 발화하므로 제약명까지는 단언하지 않고, "어떤 식으로든 거부된다" 만 검증.
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO web3_treasury_wallets"
                        + " (wallet_alias, treasury_address, kms_key_id, status, key_origin,"
                        + " created_at, updated_at)"
                        + " VALUES (?, ?, NULL, 'ACTIVE', 'IMPORTED', NOW(), NOW())",
                    CHECK_REJECT_ALIAS_KMS_NULL,
                    addr))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName(
      "[E-109] KMS-finalize cleanup — DDL 본문 핵심 블록 재실행 시 idempotent (transaction abort 없음)")
  void kmsFinalizeCleanup_constraintBlock_isIdempotentOnReplay() {
    // 9cc59777 의 의도(manual re-run + flyway:repair) 검증.
    // 마이그레이션 본문의 핵심 idempotent statement 들을 다시 실행해도 throw 하지 않아야 한다.
    assertThatCode(
            () -> {
              jdbcTemplate.execute(
                  "ALTER TABLE web3_treasury_wallets"
                      + " ALTER COLUMN kms_key_id       SET NOT NULL,"
                      + " ALTER COLUMN treasury_address SET NOT NULL,"
                      + " ALTER COLUMN status           SET NOT NULL,"
                      + " ALTER COLUMN key_origin       SET NOT NULL");

              jdbcTemplate.execute(
                  "ALTER TABLE web3_treasury_wallets"
                      + " DROP CONSTRAINT IF EXISTS ck_web3_treasury_wallets_status");
              jdbcTemplate.execute(
                  "ALTER TABLE web3_treasury_wallets"
                      + " ADD CONSTRAINT ck_web3_treasury_wallets_status"
                      + " CHECK (status IN ('ACTIVE', 'DISABLED', 'ARCHIVED'))");

              jdbcTemplate.execute(
                  "ALTER TABLE web3_treasury_wallets"
                      + " DROP CONSTRAINT IF EXISTS ck_web3_treasury_wallets_key_origin");
              jdbcTemplate.execute(
                  "ALTER TABLE web3_treasury_wallets"
                      + " ADD CONSTRAINT ck_web3_treasury_wallets_key_origin"
                      + " CHECK (key_origin IN ('IMPORTED'))");

              jdbcTemplate.execute(
                  "ALTER TABLE web3_treasury_wallets"
                      + " DROP CONSTRAINT IF EXISTS ck_web3_treasury_wallets_kms_key_id_required");
              jdbcTemplate.execute(
                  "ALTER TABLE web3_treasury_wallets"
                      + " ADD CONSTRAINT ck_web3_treasury_wallets_kms_key_id_required"
                      + " CHECK (kms_key_id IS NOT NULL AND treasury_address IS NOT NULL)");

              jdbcTemplate.execute(
                  "ALTER TABLE web3_treasury_wallets"
                      + " DROP COLUMN IF EXISTS treasury_private_key_encrypted");
            })
        .doesNotThrowAnyException();
  }
}
