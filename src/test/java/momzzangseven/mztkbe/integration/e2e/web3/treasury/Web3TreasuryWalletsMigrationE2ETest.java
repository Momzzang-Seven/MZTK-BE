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
 * docs/test/refactor-MOM-384-reward-eip-1559-change-to-kms.md}. [E-104] was rewritten under MOM-444
 * to verify V074 — the cross-row UNIQUE on {@code treasury_address} is dropped to allow multiple
 * {@code wallet_alias} rows to share one {@code treasury_address} (shared-wallet provisioning),
 * while V069's per-row {@code uk_web3_treasury_wallets_kms_key_id} UNIQUE survives.
 * [E-106]..[E-111] cover the KMS-finalize cleanup migration's CHECK constraints (status /
 * key_origin / kms_key_id_required — NOT NULL, blank, and treasury_address regex) and the migration
 * body's idempotency on replay. Uses PostgreSQL-only constructs ({@code pg_constraint}, {@code
 * NOW()}, {@code repeat()}) so it must run under the {@code integration} profile.
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
  private static final String CHECK_REJECT_ALIAS_KMS_BLANK = "check-reject-kms-blank";
  private static final String CHECK_REJECT_ALIAS_ADDR_FORMAT = "check-reject-addr-format";

  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanInsertedRows() {
    jdbcTemplate.update(
        "DELETE FROM web3_treasury_wallets WHERE wallet_alias IN (?, ?, ?, ?, ?, ?, ?)",
        LEGACY_ALIAS,
        KMS_ONLY_ALIAS,
        CHECK_REJECT_ALIAS_STATUS,
        CHECK_REJECT_ALIAS_KEY_ORIGIN,
        CHECK_REJECT_ALIAS_KMS_NULL,
        CHECK_REJECT_ALIAS_KMS_BLANK,
        CHECK_REJECT_ALIAS_ADDR_FORMAT);
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
  @DisplayName("[E-104] V074 — cross-row UNIQUE on treasury_address 가 제거됨 (shared-wallet)")
  void v074_crossRowUniqueOnTreasuryAddress_isDropped_butPerRowKmsKeyIdUniqueSurvives() {
    // V061 이 생성했던 named UNIQUE 는 V074 (MOM-444) 가 DROP 해야 한다 — 동일 treasury_address
    // 를 여러 wallet_alias 가 공유하는 shared-wallet 시나리오를 허용하기 위함.
    Integer namedCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_constraint"
                + " WHERE conrelid = 'web3_treasury_wallets'::regclass"
                + " AND contype = 'u'"
                + " AND conname = 'uk_web3_treasury_wallets_treasury_address'",
            Integer.class);
    assertThat(namedCount).isZero();

    // V007 inline UNIQUE 의 legacy 자동 생성 이름. V061 이 DROP 했고 그 이후 어떤 마이그레이션도
    // 다시 만들지 않으므로 여전히 0.
    Integer legacyCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_constraint"
                + " WHERE conrelid = 'web3_treasury_wallets'::regclass"
                + " AND conname = 'web3_treasury_keys_treasury_address_key'",
            Integer.class);
    assertThat(legacyCount).isZero();

    // V069 의 per-row kms_key_id UNIQUE 는 V074 영향 밖 — 살아있어야 한다.
    // "어떤 UNIQUE 가 사라졌고 어떤 것이 살아남았는지" 를 한 테스트에서 명시.
    Integer kmsKeyIdUniqueCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_constraint"
                + " WHERE conrelid = 'web3_treasury_wallets'::regclass"
                + " AND contype = 'u'"
                + " AND conname = 'uk_web3_treasury_wallets_kms_key_id'",
            Integer.class);
    assertThat(kmsKeyIdUniqueCount).isEqualTo(1);
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
                      + " CHECK ("
                      + "   kms_key_id IS NOT NULL"
                      + "   AND btrim(kms_key_id) <> ''"
                      + "   AND treasury_address ~ '^0x[0-9a-fA-F]{40}$'"
                      + " )");

              jdbcTemplate.execute(
                  "ALTER TABLE web3_treasury_wallets"
                      + " DROP COLUMN IF EXISTS treasury_private_key_encrypted");
            })
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("[E-110] KMS-finalize cleanup — kms_key_id 가 blank 문자열이면 거부 (btrim CHECK)")
  void kmsFinalizeCleanup_kmsKeyIdRequiredCheck_rejectsKmsKeyIdBlank() {
    String addr = "0x" + "5".repeat(40);

    // NOT NULL 은 통과하지만 strengthened CHECK (btrim(kms_key_id) <> '') 가 거부.
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO web3_treasury_wallets"
                        + " (wallet_alias, treasury_address, kms_key_id, status, key_origin,"
                        + " created_at, updated_at)"
                        + " VALUES (?, ?, '   ', 'ACTIVE', 'IMPORTED', NOW(), NOW())",
                    CHECK_REJECT_ALIAS_KMS_BLANK,
                    addr))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName(
      "[E-111] KMS-finalize cleanup — treasury_address 가 0x+40hex 형식 위반이면 거부 (regex CHECK)")
  void kmsFinalizeCleanup_kmsKeyIdRequiredCheck_rejectsMalformedTreasuryAddress() {
    // 41 hex (one extra char) — IS NOT NULL 통과, regex 거부.
    String malformedAddr = "0x" + "6".repeat(41);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO web3_treasury_wallets"
                        + " (wallet_alias, treasury_address, kms_key_id, status, key_origin,"
                        + " created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'ACTIVE', 'IMPORTED', NOW(), NOW())",
                    CHECK_REJECT_ALIAS_ADDR_FORMAT,
                    malformedAddr,
                    "alias/check-reject-addr-format"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
