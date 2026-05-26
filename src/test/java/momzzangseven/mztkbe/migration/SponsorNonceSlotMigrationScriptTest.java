package momzzangseven.mztkbe.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SponsorNonceSlotMigrationScriptTest {

  private static final Path MIGRATION =
      Path.of("src/main/resources/db/migration/V081__create_sponsor_nonce_slots.sql");
  private static final Path NORMALIZE_MIGRATION =
      Path.of("src/main/resources/db/migration/V084__normalize_sponsor_nonce_scopes.sql");
  private static final Path INDEX_MIGRATION =
      Path.of(
          "src/main/resources/db/migration/"
              + "V085__create_sponsor_nonce_lookup_indexes_concurrently.sql");
  private static final Path BACKFILL_MIGRATION =
      Path.of("src/main/resources/db/migration/V086__backfill_sponsor_nonce_slots.sql");

  @Test
  void migrationDropsLegacySenderNonceUniqueIndexBeforeAddressNormalization() throws Exception {
    String sql = Files.readString(MIGRATION);
    String normalizeSql = Files.readString(NORMALIZE_MIGRATION);
    String indexSql = Files.readString(INDEX_MIGRATION);

    assertThat(sql)
        .contains("DROP INDEX IF EXISTS uk_web3_tx_sender_nonce")
        .doesNotContain("-- flyway:executeInTransaction=false")
        .doesNotContain("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_web3_tx_sender_nonce")
        .doesNotContain("SET from_address = LOWER(from_address)")
        .doesNotContain("HAVING COUNT(*) > 1");
    assertThat(normalizeSql)
        .doesNotContain("-- flyway:executeInTransaction=false")
        .contains("HAVING COUNT(*) > 1")
        .contains("SET from_address = LOWER(from_address)")
        .doesNotContain("CREATE INDEX CONCURRENTLY");
    assertThat(indexSql)
        .contains("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_web3_tx_sender_nonce");
    assertThat(normalizeSql.indexOf("HAVING COUNT(*) > 1"))
        .isLessThan(normalizeSql.indexOf("SET from_address = LOWER(from_address)"));
  }

  @Test
  void migrationSeparatesTransactionalAndConcurrentStatements() throws Exception {
    String normalizeSql = Files.readString(NORMALIZE_MIGRATION);
    String indexSql = Files.readString(INDEX_MIGRATION);
    String backfillSql = Files.readString(BACKFILL_MIGRATION);

    assertThat(normalizeSql).doesNotContain("CREATE INDEX CONCURRENTLY");
    assertThat(indexSql)
        .contains("CREATE INDEX CONCURRENTLY")
        .contains("DROP INDEX CONCURRENTLY IF EXISTS")
        .doesNotContain("DO $$")
        .doesNotContain("INSERT INTO")
        .doesNotContain("ALTER TABLE");
    assertThat(backfillSql)
        .doesNotContain("CREATE INDEX CONCURRENTLY")
        .contains("UNIQUE USING INDEX uk_web3_tx_id_chain_sender_nonce");
    assertThat(Files.exists(Path.of(INDEX_MIGRATION + ".conf"))).isFalse();
  }

  @Test
  void migrationRecreatesNonceUniqueIndexesAfterInvalidIndexCleanup() throws Exception {
    String sql = Files.readString(MIGRATION);
    String normalizeSql = Files.readString(NORMALIZE_MIGRATION);
    String indexSql = Files.readString(INDEX_MIGRATION);

    assertThat(sql)
        .contains("DROP INDEX IF EXISTS uk_web3_tx_eip7702_authority_nonce")
        .doesNotContain(
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_web3_tx_eip7702_authority_nonce");
    assertThat(normalizeSql).doesNotContain("DROP INDEX").doesNotContain("NOT i.indisvalid");
    assertThat(indexSql)
        .contains("DROP INDEX CONCURRENTLY IF EXISTS idx_web3_tx_sender_nonce")
        .contains("DROP INDEX CONCURRENTLY IF EXISTS uk_web3_tx_id_chain_sender_nonce")
        .contains("DROP INDEX CONCURRENTLY IF EXISTS uk_web3_tx_eip7702_authority_nonce")
        .contains("DROP INDEX CONCURRENTLY IF EXISTS uk_web3_tx_non_reward_eip1559_sender_nonce")
        .contains(
            "CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uk_web3_tx_eip7702_authority_nonce")
        .contains(
            "CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS "
                + "uk_web3_tx_non_reward_eip1559_sender_nonce");
  }

  @Test
  void migrationSeparatesSchemaAndOperationalBackfill() throws Exception {
    String sql = Files.readString(MIGRATION);
    String indexSql = Files.readString(INDEX_MIGRATION);
    String backfillSql = Files.readString(BACKFILL_MIGRATION);

    assertThat(sql)
        .contains("CREATE TABLE IF NOT EXISTS web3_nonce_slots")
        .doesNotContain("INSERT INTO web3_sponsor_nonce_locks")
        .doesNotContain("INSERT INTO web3_nonce_slot_attempts")
        .doesNotContain("INSERT INTO web3_nonce_slots")
        .doesNotContain(
            "CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uk_web3_tx_id_chain_sender_nonce");
    assertThat(indexSql)
        .contains("CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uk_web3_tx_id_chain_sender_nonce")
        .doesNotContain("INSERT INTO web3_nonce_slots");
    assertThat(backfillSql)
        .contains("INSERT INTO web3_sponsor_nonce_locks")
        .contains("INSERT INTO web3_nonce_slot_attempts")
        .contains("INSERT INTO web3_nonce_slots")
        .contains("UNIQUE USING INDEX uk_web3_tx_id_chain_sender_nonce")
        .contains("VALIDATE CONSTRAINT fk_web3_nonce_slot_attempt_tx_scope")
        .contains("VALIDATE CONSTRAINT fk_web3_nonce_slots_active_tx")
        .contains("NOT VALID");
    assertThat(backfillSql.indexOf("ADD CONSTRAINT ck_web3_tx_addresses_lower"))
        .isLessThan(backfillSql.indexOf("VALIDATE CONSTRAINT ck_web3_tx_addresses_lower"));
    assertThat(backfillSql.indexOf("ADD CONSTRAINT fk_web3_nonce_slot_attempt_tx_scope"))
        .isLessThan(backfillSql.indexOf("VALIDATE CONSTRAINT fk_web3_nonce_slot_attempt_tx_scope"));
    assertThat(backfillSql.indexOf("INSERT INTO web3_nonce_slots"))
        .isLessThan(backfillSql.indexOf("ADD CONSTRAINT fk_web3_nonce_slot_attempt_tx_scope"));
  }
}
