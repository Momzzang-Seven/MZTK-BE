package momzzangseven.mztkbe.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WalletRegistrationSessionMigrationTest {

  @Test
  void migrationDefinesSessionTableAndPartialUniqueGuards() throws Exception {
    String sql =
        Files.readString(
            Path.of(
                "src/main/resources/db/migration/"
                    + "V073__create_wallet_registration_sessions.sql"));

    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS web3_wallet_registration_sessions");
    assertThat(sql)
        .contains("uk_web3_wallet_registration_sessions_non_terminal_user")
        .contains("uk_web3_wallet_registration_sessions_non_terminal_wallet")
        .contains("WHERE status IN");
    assertThat(sql).contains("uk_web3_wallet_registration_sessions_latest_intent");
    assertThat(sql).contains("ck_web3_wallet_registration_sessions_status");
  }

  @Test
  void migrationDefinesSupersededLookupIndexes() throws Exception {
    String sql =
        Files.readString(
            Path.of(
                "src/main/resources/db/migration/"
                    + "V079__add_wallet_registration_superseded_lookup_indexes.sql"));

    assertThat(sql)
        .contains("idx_web3_wallet_registration_sessions_user_created_id")
        .contains("ON web3_wallet_registration_sessions(user_id, created_at DESC, id DESC)")
        .contains("idx_web3_wallet_registration_sessions_wallet_created_id")
        .contains("ON web3_wallet_registration_sessions(wallet_address, created_at DESC, id DESC)");
  }
}
