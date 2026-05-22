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
  @DisplayName("V078 keeps marketplace indexes in the same transactional migration before deploy")
  void marketplaceIndexesStayInTransactionalV078BeforeDeploy() throws IOException {
    String v078 = migration("V078__add_marketplace_user_escrow_state.sql");

    assertThat(v078).doesNotContain("CONCURRENTLY").doesNotContain("executeInTransaction=false");
    assertThat(v078)
        .contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_class_reservations_order_key")
        .contains(
            "CREATE UNIQUE INDEX IF NOT EXISTS "
                + "uk_class_reservations_current_execution_intent_public_id")
        .contains("CREATE INDEX IF NOT EXISTS " + "idx_class_reservations_escrow_flow_status")
        .contains(
            "CREATE UNIQUE INDEX IF NOT EXISTS "
                + "uk_class_reservations_active_buyer_slot_datetime")
        .contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_trainer_strike_records_source");
  }

  @Test
  @DisplayName("V078 preflight blocks dirty marketplace rows before adding constraints")
  void marketplaceMigrationPreflightBlocksDirtyData() throws IOException {
    String v078 = migration("V078__add_marketplace_user_escrow_state.sql");

    assertThat(v078)
        .contains("Duplicate active class_reservations must be repaired before V078")
        .contains("GROUP BY user_id, class_slot_id, reservation_date, reservation_time")
        .contains("Active ESCROW_DISPATCH_PENDING rows require manual repair before V078")
        .contains("tx_hash = 'ESCROW_DISPATCH_PENDING'");
  }

  @Test
  @DisplayName("V078 large-table checks are added before validation")
  void marketplaceMigrationClassReservationChecksAreNotValidBeforeValidation() throws IOException {
    String v078 = migration("V078__add_marketplace_user_escrow_state.sql");

    assertThat(v078)
        .contains("ADD COLUMN IF NOT EXISTS escrow_status")
        .contains("ADD CONSTRAINT chk_class_reservations_status CHECK")
        .contains(") NOT VALID")
        .contains("VALIDATE CONSTRAINT chk_class_reservations_status")
        .contains("'PURCHASE_PREPARING', 'PURCHASE_PENDING'")
        .contains("'DEADLINE_REFUND_AVAILABLE', 'DEADLINE_REFUND_PENDING'")
        .contains("status IN ('PREPARING', 'PREPARATION_FAILED', 'STALE')")
        .contains("CREATE INDEX IF NOT EXISTS idx_marketplace_reservation_escrows_flow_status")
        .contains(
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_marketplace_reservation_action_states_active")
        .contains("CREATE INDEX IF NOT EXISTS idx_reservation_create_idempotency_reservation_id");
  }

  @Test
  @DisplayName("V079 adds marketplace admin pending and provenance constraints")
  void marketplaceAdminExecutionSurfaceMigration() throws IOException {
    String v079 = migration("V079__add_marketplace_admin_execution_surface.sql");

    assertThat(v079)
        .contains("ADD COLUMN IF NOT EXISTS resolved_by")
        .contains("ADD COLUMN IF NOT EXISTS terminal_reason_code")
        .contains("'ADMIN_REFUND_PENDING', 'ADMIN_SETTLE_PENDING'")
        .contains("'ADMIN_REFUND', 'ADMIN_SETTLE'")
        .contains("ADD COLUMN IF NOT EXISTS request_source")
        .contains("SET request_source = 'USER'")
        .contains("ALTER COLUMN actor_user_id DROP NOT NULL")
        .contains("reason_code IS NULL OR reason_code IN")
        .contains("request_source IN ('USER', 'MANUAL_ADMIN', 'SCHEDULER')")
        .contains("request_source = 'SCHEDULER'")
        .contains("actor_type = 'SYSTEM'")
        .contains("actor_user_id IS NULL")
        .contains(
            "CREATE INDEX IF NOT EXISTS idx_marketplace_action_states_admin_preparing_expired")
        .contains("WHERE status = 'PREPARING'")
        .contains("action_type IN ('ADMIN_REFUND', 'ADMIN_SETTLE')");
  }

  private String migration(String filename) throws IOException {
    return Files.readString(MIGRATION_DIR.resolve(filename));
  }
}
