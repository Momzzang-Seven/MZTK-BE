package momzzangseven.mztkbe.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SponsorNonceSlotMigrationScriptTest {

  private static final Path MIGRATION =
      Path.of("src/main/resources/db/migration/V081__create_sponsor_nonce_slots.sql");

  @Test
  void migrationDropsLegacySenderNonceUniqueIndexBeforeAddressNormalization() throws Exception {
    String sql = Files.readString(MIGRATION);

    assertThat(sql)
        .contains("DROP INDEX IF EXISTS uk_web3_tx_sender_nonce")
        .contains("-- flyway:executeInTransaction=false")
        .contains("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_web3_tx_sender_nonce")
        .doesNotContain("HAVING COUNT(*) > 1");
    assertThat(sql.indexOf("DROP INDEX IF EXISTS uk_web3_tx_sender_nonce"))
        .isLessThan(sql.indexOf("SET from_address = LOWER(from_address)"));
  }

  @Test
  void migrationDropsEip7702AuthorityNonceUniqueIndexBeforeAddressNormalization() throws Exception {
    String sql = Files.readString(MIGRATION);

    assertThat(sql)
        .contains("DROP INDEX IF EXISTS uk_web3_tx_eip7702_authority_nonce")
        .contains(
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_web3_tx_eip7702_authority_nonce");
    assertThat(sql.indexOf("DROP INDEX IF EXISTS uk_web3_tx_eip7702_authority_nonce"))
        .isLessThan(sql.indexOf("SET from_address = LOWER(from_address)"));
  }

  @Test
  void migrationKeepsSponsorNonceBackfillInUndeployedV081() throws Exception {
    String sql = Files.readString(MIGRATION);

    assertThat(sql)
        .contains("INSERT INTO web3_sponsor_nonce_locks")
        .contains("INSERT INTO web3_nonce_slot_attempts")
        .contains("INSERT INTO web3_nonce_slots")
        .contains(
            "CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uk_web3_tx_id_chain_sender_nonce")
        .contains("NOT VALID");
  }
}
