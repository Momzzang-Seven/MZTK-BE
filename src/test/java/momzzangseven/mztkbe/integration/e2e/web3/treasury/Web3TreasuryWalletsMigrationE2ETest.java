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
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * E2E migration invariants for {@code web3_treasury_wallets} (KMS column additions through the
 * KMS-finalize cleanup migration) and {@code web3_treasury_kms_audits}.
 *
 * <p>Cases [E-102]..[E-105] from {@code
 * docs/test/refactor-MOM-384-reward-eip-1559-change-to-kms.md}. [E-106]..[E-111] cover the
 * KMS-finalize cleanup migration's CHECK constraints (status / key_origin / kms_key_id_required —
 * NOT NULL, blank, and treasury_address regex) and the migration body's idempotency on replay. Uses
 * PostgreSQL-only constructs ({@code pg_constraint}, {@code NOW()}, {@code repeat()}) so it must
 * run under the {@code integration} profile.
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
  private static final String PAIR_ALIAS_A = "pairing-a";
  private static final String PAIR_ALIAS_B = "pairing-b";
  private static final String SIBLING_ALIAS_A = "sibling-a";
  private static final String SIBLING_ALIAS_B = "sibling-b";
  private static final String DUP_ALIAS = "dup-alias";

  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanInsertedRows() {
    jdbcTemplate.update(
        "DELETE FROM web3_treasury_wallets WHERE wallet_alias IN"
            + " (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        LEGACY_ALIAS,
        KMS_ONLY_ALIAS,
        CHECK_REJECT_ALIAS_STATUS,
        CHECK_REJECT_ALIAS_KEY_ORIGIN,
        CHECK_REJECT_ALIAS_KMS_NULL,
        CHECK_REJECT_ALIAS_KMS_BLANK,
        CHECK_REJECT_ALIAS_ADDR_FORMAT,
        PAIR_ALIAS_A,
        PAIR_ALIAS_B,
        SIBLING_ALIAS_A,
        SIBLING_ALIAS_B,
        DUP_ALIAS);
  }

  /**
   * Inserts an ACTIVE / IMPORTED wallet row directly via JDBC (bypassing the application layer).
   */
  private void insertWalletRow(String alias, String address, String kmsKeyId) {
    jdbcTemplate.update(
        "INSERT INTO web3_treasury_wallets"
            + " (wallet_alias, treasury_address, kms_key_id, status, key_origin,"
            + " created_at, updated_at)"
            + " VALUES (?, ?, ?, 'ACTIVE', 'IMPORTED', NOW(), NOW())",
        alias,
        address,
        kmsKeyId);
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
  @DisplayName("[E-104] V073 — cohort 공유를 위해 per-column UNIQUE 제약 DROP, 비유니크 인덱스 + pairing 트리거로 대체")
  void v073_relaxesPerColumnUniqueness_replacedByIndexesAndPairingTrigger() {
    // V061/V069 가 명명한 per-column UNIQUE 제약 2개는 V073 가 cohort 공유를 위해 drop 했다.
    Integer uniqueConstraintCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_constraint"
                + " WHERE conrelid = 'web3_treasury_wallets'::regclass"
                + " AND contype = 'u'"
                + " AND conname IN ('uk_web3_treasury_wallets_treasury_address',"
                + " 'uk_web3_treasury_wallets_kms_key_id')",
            Integer.class);
    assertThat(uniqueConstraintCount).isZero();

    // V007 자동 생성 이름은 V061 에서 이미 정리됐고 그대로 남아 있어선 안 된다.
    Integer legacyCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_constraint"
                + " WHERE conrelid = 'web3_treasury_wallets'::regclass"
                + " AND conname = 'web3_treasury_keys_treasury_address_key'",
            Integer.class);
    assertThat(legacyCount).isZero();

    // 대체물 [1] — cohort 조회/스캔용 비유니크 인덱스 2개.
    List<String> indexNames =
        jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes"
                + " WHERE tablename = 'web3_treasury_wallets'"
                + " AND indexname IN ('idx_web3_treasury_wallets_treasury_address',"
                + " 'idx_web3_treasury_wallets_kms_key_id')"
                + " ORDER BY indexname",
            String.class);
    assertThat(indexNames)
        .containsExactly(
            "idx_web3_treasury_wallets_kms_key_id", "idx_web3_treasury_wallets_treasury_address");

    // 대체물 [2] — treasury_address <-> kms_key_id 1:1 invariant 를 지키는 pairing 트리거.
    Integer pairingTriggerCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_trigger"
                + " WHERE tgrelid = 'web3_treasury_wallets'::regclass"
                + " AND tgname = 'trg_web3_treasury_wallets_pairing'",
            Integer.class);
    assertThat(pairingTriggerCount).isEqualTo(1);
  }

  @Test
  @DisplayName("[E-2] pairing 트리거 — 이미 바인딩된 treasury_address 에 다른 kms_key_id INSERT 거부")
  void pairingTrigger_rejectsSecondKeyForBoundAddress() {
    String addr = "0x" + "a".repeat(40);
    insertWalletRow(PAIR_ALIAS_A, addr, "kms-pair-key-1");

    // the pairing trigger uses RAISE EXCEPTION (SQLSTATE P0001) — surfaces as a DataAccessException
    assertThatThrownBy(() -> insertWalletRow(PAIR_ALIAS_B, addr, "kms-pair-key-2"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("already bound to a different kms_key_id");

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_treasury_wallets WHERE treasury_address = ?",
            Integer.class,
            addr);
    assertThat(count).isEqualTo(1);
  }

  @Test
  @DisplayName("[E-3] pairing 트리거 — 이미 바인딩된 kms_key_id 에 다른 treasury_address INSERT 거부")
  void pairingTrigger_rejectsSecondAddressForBoundKey() {
    String sharedKey = "kms-pair-shared-key";
    insertWalletRow(PAIR_ALIAS_A, "0x" + "a".repeat(40), sharedKey);

    assertThatThrownBy(() -> insertWalletRow(PAIR_ALIAS_B, "0x" + "b".repeat(40), sharedKey))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("already bound to a different treasury_address");

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_treasury_wallets WHERE kms_key_id = ?",
            Integer.class,
            sharedKey);
    assertThat(count).isEqualTo(1);
  }

  @Test
  @DisplayName("[E-4] pairing 트리거 — 동일 (treasury_address, kms_key_id) 쌍을 공유하는 sibling row 는 허용")
  void pairingTrigger_allowsSiblingSharingExactPair() {
    String addr = "0x" + "c".repeat(40);
    String sharedKey = "kms-cohort-shared-key";
    insertWalletRow(SIBLING_ALIAS_A, addr, sharedKey);

    assertThatCode(() -> insertWalletRow(SIBLING_ALIAS_B, addr, sharedKey))
        .doesNotThrowAnyException();

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM web3_treasury_wallets WHERE treasury_address = ?",
            Integer.class,
            addr);
    assertThat(count).isEqualTo(2);
  }

  @Test
  @DisplayName("[E-5] pairing 트리거 — treasury_address/kms_key_id 를 바꾸지 않는 UPDATE 는 허용")
  void pairingTrigger_allowsUpdateThatDoesNotChangeAddressOrKey() {
    String addr = "0x" + "d".repeat(40);
    String sharedKey = "kms-update-shared-key";
    insertWalletRow(SIBLING_ALIAS_A, addr, sharedKey);
    insertWalletRow(SIBLING_ALIAS_B, addr, sharedKey);

    assertThatCode(
            () ->
                jdbcTemplate.update(
                    "UPDATE web3_treasury_wallets SET status = 'DISABLED', disabled_at = NOW()"
                        + " WHERE wallet_alias IN (?, ?)",
                    SIBLING_ALIAS_A,
                    SIBLING_ALIAS_B))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("[E-8] V073 이후에도 wallet_alias UNIQUE 는 유지 — 동일 alias 두 번째 INSERT 거부")
  void v073_keepsWalletAliasUnique() {
    String sharedKey = "kms-dup-alias-key";
    insertWalletRow(DUP_ALIAS, "0x" + "e".repeat(40), sharedKey);

    assertThatThrownBy(() -> insertWalletRow(DUP_ALIAS, "0x" + "e".repeat(40), sharedKey))
        .isInstanceOf(DataIntegrityViolationException.class);
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
