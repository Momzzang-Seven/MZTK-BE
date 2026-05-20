package momzzangseven.mztkbe.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.MztkBeApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Guards Flyway migration correctness in the real PostgreSQL integration test tier.
 *
 * <p>This test intentionally has no {@code e2e} tag. When {@code DB_URL_E2E} is present, the
 * default {@code test} task runs it against real PostgreSQL. Booting the Spring context runs Flyway
 * end-to-end with {@code validate-on-migrate=true}, and Hibernate re-checks every {@code @Entity}
 * against the resulting schema via {@code ddl-auto=validate}. Any checksum mismatch, out-of-order
 * version, missing migration, or entity/column drift fails context startup — which fails this test.
 */
@DisplayName("[Migration] Flyway migrations apply cleanly and match JPA entities")
@EnabledIfEnvironmentVariable(named = "DB_URL_E2E", matches = ".+")
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

  @Test
  @DisplayName("marketplace reservation user escrow schema supports MOM-313 local state")
  void marketplaceReservationUserEscrowSchemaSupportsLocalState() {
    assertThat(columnExists("class_reservations", "escrow_status")).isTrue();
    assertThat(columnExists("class_reservations", "escrow_flow")).isTrue();
    assertThat(columnExists("class_reservations", "order_key")).isTrue();
    assertThat(columnExists("class_reservations", "current_execution_intent_public_id")).isTrue();
    assertThat(columnExists("class_reservations", "contract_deadline_epoch_seconds")).isTrue();
    assertThat(columnExists("class_reservations", "contract_deadline_at")).isTrue();
    assertThat(columnExists("marketplace_reservation_escrows", "reservation_id")).isTrue();
    assertThat(columnExists("marketplace_reservation_escrows", "price_base_units")).isTrue();
    assertThat(columnExists("marketplace_reservation_action_states", "attempt_token")).isTrue();
    assertThat(columnExists("marketplace_reservation_action_states", "execution_intent_public_id"))
        .isTrue();
    assertThat(columnExists("reservation_create_idempotency_keys", "escrow_id")).isTrue();
    assertThat(columnExists("reservation_create_idempotency_keys", "action_state_id")).isTrue();
    assertThat(
            columnExists(
                "reservation_create_idempotency_keys", "current_execution_intent_public_id"))
        .isFalse();
    assertThat(indexExists("uk_class_reservations_order_key")).isTrue();
    assertThat(indexExists("uk_class_reservations_active_buyer_slot_datetime")).isTrue();
    assertThat(indexExists("uk_marketplace_reservation_action_states_active")).isTrue();
    assertThat(indexExists("uk_reservation_create_idempotency_buyer_key")).isTrue();
    assertThat(indexExists("uk_reservation_slot_date_locks_slot_date")).isTrue();
    assertThat(indexExists("uk_trainer_strike_records_source")).isTrue();

    String statusConstraint = checkClause("class_reservations", "chk_class_reservations_status");
    assertThat(statusConstraint)
        .contains("HOLDING")
        .contains("PURCHASE_PREPARING")
        .contains("PURCHASE_PENDING")
        .contains("DEADLINE_REFUND_AVAILABLE")
        .contains("DEADLINE_REFUNDED");

    String escrowStatusConstraint =
        checkClause(
            "marketplace_reservation_escrows", "chk_marketplace_reservation_escrows_status");
    assertThat(escrowStatusConstraint)
        .contains("PURCHASE_PREPARING")
        .contains("PURCHASE_PENDING")
        .contains("LOCKED")
        .contains("CANCEL_PENDING")
        .contains("REJECT_PENDING")
        .contains("CONFIRM_PENDING")
        .contains("DEADLINE_REFUND_AVAILABLE")
        .contains("DEADLINE_REFUND_PENDING")
        .contains("DEADLINE_REFUNDED");

    String actionStateStatusConstraint =
        checkClause(
            "marketplace_reservation_action_states",
            "chk_marketplace_reservation_action_states_status");
    assertThat(actionStateStatusConstraint)
        .contains("PREPARING")
        .contains("INTENT_BOUND")
        .contains("PREPARATION_FAILED")
        .contains("STALE");

    String actionStateBoundIntentConstraint =
        checkClause(
            "marketplace_reservation_action_states",
            "chk_marketplace_reservation_action_states_bound_intent");
    assertThat(actionStateBoundIntentConstraint)
        .contains("PREPARING")
        .contains("PREPARATION_FAILED")
        .contains("STALE");

    String idempotencyStatusConstraint =
        checkClause(
            "reservation_create_idempotency_keys", "chk_reservation_create_idempotency_status");
    assertThat(idempotencyStatusConstraint)
        .contains("PREPARING")
        .contains("BOUND")
        .contains("COMPLETED")
        .doesNotContain("INTENT_CREATED");

    String deadlineConstraint =
        checkClause("class_reservations", "chk_class_reservations_contract_deadline_pair");
    assertThat(deadlineConstraint)
        .contains("contract_deadline_epoch_seconds")
        .contains("contract_deadline_at");
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

  private String checkClause(String tableName, String constraintName) {
    return jdbcTemplate.queryForObject(
        """
        SELECT cc.check_clause
        FROM information_schema.table_constraints tc
        JOIN information_schema.check_constraints cc
          ON tc.constraint_schema = cc.constraint_schema
         AND tc.constraint_name = cc.constraint_name
        WHERE tc.table_name = ?
          AND tc.constraint_name = ?
        """,
        String.class,
        tableName,
        constraintName);
  }
}
