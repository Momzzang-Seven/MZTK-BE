package momzzangseven.mztkbe.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SponsorNonceSlotMigrationScriptTest {

  private static final Path MIGRATION =
      Path.of("src/main/resources/db/migration/V081__create_sponsor_nonce_slots.sql");
  private static final Path BACKFILL_MIGRATION =
      Path.of(
          "src/main/resources/db/migration/"
              + "V084__backfill_sponsor_nonce_slots_non_transactionally.sql");

  @Test
  void migrationDropsLegacySenderNonceUniqueIndexBeforeAddressNormalization() throws Exception {
    String sql = Files.readString(MIGRATION);
    String backfillSql = Files.readString(BACKFILL_MIGRATION);

    assertThat(sql)
        .contains("DROP INDEX IF EXISTS uk_web3_tx_sender_nonce")
        .doesNotContain("-- flyway:executeInTransaction=false")
        .doesNotContain("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_web3_tx_sender_nonce")
        .doesNotContain("SET from_address = LOWER(from_address)")
        .doesNotContain("HAVING COUNT(*) > 1");
    assertThat(backfillSql)
        .contains("-- flyway:executeInTransaction=false")
        .contains("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_web3_tx_sender_nonce")
        .contains("SET from_address = LOWER(from_address)");
  }

  @Test
  void migrationDropsEip7702AuthorityNonceUniqueIndexBeforeAddressNormalization() throws Exception {
    String sql = Files.readString(MIGRATION);
    String backfillSql = Files.readString(BACKFILL_MIGRATION);

    assertThat(sql)
        .contains("DROP INDEX IF EXISTS uk_web3_tx_eip7702_authority_nonce")
        .doesNotContain(
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_web3_tx_eip7702_authority_nonce");
    assertThat(backfillSql)
        .contains("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_web3_tx_eip7702_authority_nonce");
  }

  @Test
  void migrationSeparatesSchemaAndOperationalBackfill() throws Exception {
    String sql = Files.readString(MIGRATION);
    String backfillSql = Files.readString(BACKFILL_MIGRATION);

    assertThat(sql)
        .contains("CREATE TABLE IF NOT EXISTS web3_nonce_slots")
        .doesNotContain("INSERT INTO web3_sponsor_nonce_locks")
        .doesNotContain("INSERT INTO web3_nonce_slot_attempts")
        .doesNotContain("INSERT INTO web3_nonce_slots")
        .doesNotContain(
            "CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uk_web3_tx_id_chain_sender_nonce");
    assertThat(backfillSql)
        .contains("INSERT INTO web3_sponsor_nonce_locks")
        .contains("INSERT INTO web3_nonce_slot_attempts")
        .contains("INSERT INTO web3_nonce_slots")
        .contains("CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uk_web3_tx_id_chain_sender_nonce")
        .contains("NOT VALID");
  }
}
