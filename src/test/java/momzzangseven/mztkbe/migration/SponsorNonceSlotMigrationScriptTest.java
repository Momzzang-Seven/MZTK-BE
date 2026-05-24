package momzzangseven.mztkbe.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SponsorNonceSlotMigrationScriptTest {

  private static final Path MIGRATION =
      Path.of("src/main/resources/db/migration/V081__create_sponsor_nonce_slots.sql");

  @Test
  void migrationReplacesLegacySenderNonceUniqueIndexWithLookupIndex() throws Exception {
    String sql = Files.readString(MIGRATION);

    assertThat(sql)
        .contains("DROP INDEX IF EXISTS uk_web3_tx_sender_nonce")
        .contains("CREATE INDEX IF NOT EXISTS idx_web3_tx_sender_nonce")
        .doesNotContain("HAVING COUNT(*) > 1");
  }

  @Test
  void migrationReplacesEip7702AuthorityNonceUniqueIndexWithLookupIndex() throws Exception {
    String sql = Files.readString(MIGRATION);

    assertThat(sql)
        .contains("DROP INDEX IF EXISTS uk_web3_tx_eip7702_authority_nonce")
        .contains("CREATE INDEX IF NOT EXISTS idx_web3_tx_eip7702_authority_nonce");
  }
}
