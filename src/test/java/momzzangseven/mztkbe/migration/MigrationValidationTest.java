package momzzangseven.mztkbe.migration;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.MztkBeApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Guards Flyway migration correctness on every CI run.
 *
 * <p>Intentionally <strong>not</strong> tagged {@code @Tag("e2e")} so it is included in {@code
 * ./gradlew test} and cannot be bypassed by skipping E2E suites. Booting the Spring context under
 * the {@code integration} profile runs Flyway end-to-end against real PostgreSQL with {@code
 * validate-on-migrate=true}, and Hibernate re-checks every {@code @Entity} against the resulting
 * schema via {@code ddl-auto=validate}. Any checksum mismatch, out-of-order version, missing
 * migration, or entity/column drift fails context startup — which fails this test.
 */
@DisplayName("[Migration] Flyway migrations apply cleanly and match JPA entities")
@ActiveProfiles("integration")
@SpringBootTest(
    classes = MztkBeApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "mztk.admin.bootstrap.enabled=false")
class MigrationValidationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void contextLoads() {}

  @Test
  @DisplayName("admin_board_moderation_actions target index is non-unique")
  void adminBoardModerationActionsTargetIndexIsNonUnique() {
    Boolean targetIndexUnique =
        jdbcTemplate.queryForObject(
            """
            SELECT i.indisunique
            FROM pg_index i
            JOIN pg_class c ON c.oid = i.indexrelid
            WHERE c.relname = 'idx_admin_board_moderation_actions_target'
            """,
            Boolean.class);

    assertThat(targetIndexUnique).isFalse();
  }

  @Test
  @DisplayName("admin_board_moderation_actions operator_id has no users foreign key")
  void adminBoardModerationActionsOperatorIdHasNoForeignKey() {
    Integer foreignKeyCount =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
              ON tc.constraint_schema = kcu.constraint_schema
             AND tc.constraint_name = kcu.constraint_name
            WHERE tc.table_name = 'admin_board_moderation_actions'
              AND tc.constraint_type = 'FOREIGN KEY'
              AND kcu.column_name = 'operator_id'
            """,
            Integer.class);

    assertThat(foreignKeyCount).isZero();
  }
}
