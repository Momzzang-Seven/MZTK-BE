package momzzangseven.mztkbe.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.MztkBeApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
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

  @Test
  @DisplayName("web3_transactions reference uniqueness is level-reward only")
  void web3TransactionReferenceUniquenessIsLevelRewardOnly() {
    String suffix = "migration-" + System.nanoTime();

    insertWeb3Transaction("idem-user-server-1-" + suffix, "USER_TO_SERVER", "question-" + suffix);
    insertWeb3Transaction("idem-user-server-2-" + suffix, "USER_TO_SERVER", "question-" + suffix);
    insertWeb3Transaction("idem-user-user-1-" + suffix, "USER_TO_USER", "answer-" + suffix);
    insertWeb3Transaction("idem-user-user-2-" + suffix, "USER_TO_USER", "answer-" + suffix);
    insertWeb3Transaction("idem-server-user-1-" + suffix, "SERVER_TO_USER", "server-" + suffix);
    insertWeb3Transaction("idem-server-user-2-" + suffix, "SERVER_TO_USER", "server-" + suffix);

    insertWeb3Transaction("idem-level-1-" + suffix, "LEVEL_UP_REWARD", "level-" + suffix);
    assertThatThrownBy(
            () ->
                insertWeb3Transaction(
                    "idem-level-2-" + suffix, "LEVEL_UP_REWARD", "level-" + suffix))
        .isInstanceOf(DataAccessException.class);

    assertThatThrownBy(
            () ->
                insertWeb3Transaction(
                    "idem-level-1-" + suffix, "USER_TO_SERVER", "other-" + suffix))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  @DisplayName("web3_transactions reference indexes match duplicate policy")
  void web3TransactionReferenceIndexesMatchDuplicatePolicy() {
    Boolean referenceIndexUnique = isIndexUnique("idx_web3_tx_reference");
    Boolean levelRewardReferenceIndexUnique = isIndexUnique("uk_web3_tx_level_reward_reference");
    String levelRewardPredicate =
        jdbcTemplate.queryForObject(
            """
            SELECT pg_get_expr(i.indpred, i.indrelid)
            FROM pg_index i
            JOIN pg_class c ON c.oid = i.indexrelid
            WHERE c.relname = 'uk_web3_tx_level_reward_reference'
            """,
            String.class);

    assertThat(referenceIndexUnique).isFalse();
    assertThat(levelRewardReferenceIndexUnique).isTrue();
    assertThat(levelRewardPredicate).contains("LEVEL_UP_REWARD");
  }

  @Test
  @DisplayName("qna_question_update_states schema supports version history and reconciliation")
  void qnaQuestionUpdateStatesSchemaSupportsVersionHistory() {
    assertThat(indexExists("uk_qna_question_update_states_post_version")).isTrue();
    assertThat(isIndexUnique("uk_qna_question_update_states_post_version")).isTrue();
    assertThat(indexExists("uk_qna_question_update_states_token")).isTrue();
    assertThat(isIndexUnique("uk_qna_question_update_states_token")).isTrue();
    assertThat(indexExists("uk_qna_question_update_states_intent_public_id")).isTrue();
    assertThat(isIndexUnique("uk_qna_question_update_states_intent_public_id")).isTrue();
    assertThat(indexExists("idx_qna_question_update_states_post_latest")).isTrue();
    assertThat(indexExists("idx_qna_question_update_states_status_updated_at")).isTrue();
    assertThat(columnExists("qna_question_update_states", "preparation_retryable")).isTrue();

    String statusConstraint =
        jdbcTemplate.queryForObject(
            """
            SELECT cc.check_clause
            FROM information_schema.table_constraints tc
            JOIN information_schema.check_constraints cc
              ON tc.constraint_schema = cc.constraint_schema
             AND tc.constraint_name = cc.constraint_name
            WHERE tc.table_name = 'qna_question_update_states'
              AND tc.constraint_name = 'ck_qna_question_update_states_status'
            """,
            String.class);

    assertThat(statusConstraint)
        .contains("PREPARING")
        .contains("PREPARATION_FAILED")
        .contains("INTENT_BOUND")
        .contains("CONFIRMED")
        .contains("STALE");
  }

  private void insertWeb3Transaction(
      String idempotencyKey, String referenceType, String referenceId) {
    jdbcTemplate.update(
        """
        INSERT INTO web3_transactions (
            idempotency_key,
            reference_type,
            reference_id,
            from_address,
            to_address,
            amount_wei,
            status
        ) VALUES (?, ?, ?, ?, ?, 0, 'CREATED')
        """,
        idempotencyKey,
        referenceType,
        referenceId,
        "0x" + "1".repeat(40),
        "0x" + "2".repeat(40));
  }

  private boolean indexExists(String indexName) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_class WHERE relname = ?", Integer.class, indexName);
    return count != null && count > 0;
  }

  private Boolean isIndexUnique(String indexName) {
    return jdbcTemplate.queryForObject(
        """
        SELECT i.indisunique
        FROM pg_index i
        JOIN pg_class c ON c.oid = i.indexrelid
        WHERE c.relname = ?
        """,
        Boolean.class,
        indexName);
  }

  private boolean columnExists(String tableName, String columnName) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_name = ? AND column_name = ?
            """,
            Integer.class,
            tableName,
            columnName);
    return count != null && count > 0;
  }
}
