package momzzangseven.mztkbe.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SponsorNonceSlotMigrationScriptTest {

  private static final Path MIGRATION =
      Path.of("src/main/resources/db/migration/V081__create_sponsor_nonce_slots.sql");
  private static final Path CONCURRENT_INDEX_MIGRATION =
      Path.of(
          "src/main/resources/db/migration/"
              + "V084__create_web3_transaction_lookup_indexes_concurrently.sql");

  @Test
  void migrationDropsLegacySenderNonceUniqueIndexBeforeAddressNormalization() throws Exception {
    String sql = Files.readString(MIGRATION);
    String concurrentSql = Files.readString(CONCURRENT_INDEX_MIGRATION);

    assertThat(sql)
        .contains("DROP INDEX IF EXISTS uk_web3_tx_sender_nonce")
        .doesNotContain("CREATE INDEX IF NOT EXISTS idx_web3_tx_sender_nonce")
        .doesNotContain("HAVING COUNT(*) > 1");
    assertThat(sql.indexOf("DROP INDEX IF EXISTS uk_web3_tx_sender_nonce"))
        .isLessThan(sql.indexOf("SET from_address = LOWER(from_address)"));
    assertThat(concurrentSql)
        .contains("-- flyway:executeInTransaction=false")
        .contains("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_web3_tx_sender_nonce");
  }

  @Test
  void migrationDropsEip7702AuthorityNonceUniqueIndexBeforeAddressNormalization() throws Exception {
    String sql = Files.readString(MIGRATION);
    String concurrentSql = Files.readString(CONCURRENT_INDEX_MIGRATION);

    assertThat(sql)
        .contains("DROP INDEX IF EXISTS uk_web3_tx_eip7702_authority_nonce")
        .doesNotContain("CREATE INDEX IF NOT EXISTS idx_web3_tx_eip7702_authority_nonce");
    assertThat(sql.indexOf("DROP INDEX IF EXISTS uk_web3_tx_eip7702_authority_nonce"))
        .isLessThan(sql.indexOf("SET from_address = LOWER(from_address)"));
    assertThat(concurrentSql)
        .contains("-- flyway:executeInTransaction=false")
        .contains("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_web3_tx_eip7702_authority_nonce");
  }
}
