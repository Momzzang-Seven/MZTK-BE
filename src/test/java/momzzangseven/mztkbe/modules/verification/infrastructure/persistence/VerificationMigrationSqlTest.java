package momzzangseven.mztkbe.modules.verification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VerificationMigrationSqlTest {

  @Test
  void migrationContainsVerificationTablesAndPostgresSpecificIndexes() throws IOException {
    Path migration =
        Path.of("src/main/resources/db/migration/V018__create_verification_tables.sql");

    assertThat(migration).exists();

    String sql = Files.readString(migration).replaceAll("\\s+", " ");

    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS verification_requests");
    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS verification_signals");
    assertThat(sql).contains("CONSTRAINT ck_retry_schedule");
    assertThat(sql).contains("CONSTRAINT ck_terminal_reason");
    assertThat(sql).contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_verification_daily_gate");
    assertThat(sql)
        .contains(
            "WHERE verification_kind IN ('WORKOUT_PHOTO', 'WORKOUT_RECORD') "
                + "AND status IN ('PENDING', 'ANALYZING', 'RETRY_SCHEDULED', 'VERIFIED')");
    assertThat(sql).contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_verification_signal_unique");
  }
}
