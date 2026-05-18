package momzzangseven.mztkbe.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarketplaceMigrationScriptTest {

  private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

  @Test
  @DisplayName("marketplace indexes on existing tables are created concurrently in V074")
  void marketplaceExistingTableIndexesUseConcurrentCreation() throws IOException {
    String v074 = migration("V074__add_marketplace_user_escrow_state.sql");

    assertThat(v074).contains("-- flyway:executeInTransaction=false");
    assertThat(v074)
        .contains("CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uk_class_reservations_order_key")
        .contains(
            "CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS "
                + "uk_class_reservations_current_execution_intent_public_id")
        .contains(
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS "
                + "idx_class_reservations_escrow_flow_status")
        .contains(
            "CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS "
                + "uk_class_reservations_active_buyer_slot_datetime")
        .contains(
            "CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uk_trainer_strike_records_source");
  }

  private String migration(String filename) throws IOException {
    return Files.readString(MIGRATION_DIR.resolve(filename));
  }
}
