package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@DisplayName("AnswerPublicationReconciliationJdbcAdapter")
class AnswerPublicationReconciliationJdbcAdapterTest {

  private JdbcTemplate jdbcTemplate;
  private AnswerPublicationReconciliationJdbcAdapter adapter;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.h2.Driver");
    dataSource.setUrl(
        "jdbc:h2:mem:"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    dataSource.setUsername("sa");
    dataSource.setPassword("");

    jdbcTemplate = new JdbcTemplate(dataSource);
    adapter = new AnswerPublicationReconciliationJdbcAdapter(jdbcTemplate);
    createSchema();
  }

  @Test
  @DisplayName("terminal answer update intent moves bound update state to failed")
  void reconcileTerminalUpdateFailuresMarksIntentBoundStateFailed() {
    insertExecutionIntent(
        "intent-update-terminal",
        "ANSWER",
        "QNA_ANSWER_UPDATE",
        "FAILED_ONCHAIN",
        "RPC_UNAVAILABLE");
    insertExecutionIntent(
        "intent-update-active", "ANSWER", "QNA_ANSWER_UPDATE", "PENDING_ONCHAIN", null);
    insertAnswerUpdateState(1L, 201L, 1L, "intent-update-terminal", "INTENT_BOUND");
    insertAnswerUpdateState(2L, 202L, 1L, "intent-update-active", "INTENT_BOUND");

    int updatedRows = adapter.reconcileTerminalUpdateFailures(100);

    assertThat(updatedRows).isEqualTo(1);
    assertThat(loadAnswerUpdateStatus(1L)).isEqualTo("FAILED");
    assertThat(loadAnswerUpdateErrorReason(1L)).isEqualTo("RPC_UNAVAILABLE");
    assertThat(loadAnswerUpdateStatus(2L)).isEqualTo("INTENT_BOUND");
  }

  @Test
  @DisplayName("terminal answer delete intent clears pending delete state")
  void reconcileTerminalDeleteRollbacksClearsPendingDeleteState() {
    insertExecutionIntent(
        "intent-delete-terminal",
        "ANSWER",
        "QNA_ANSWER_DELETE",
        "EXPIRED",
        "EXECUTION_INTENT_EXPIRED");
    insertExecutionIntent(
        "intent-delete-active", "ANSWER", "QNA_ANSWER_DELETE", "PENDING_ONCHAIN", null);
    insertAnswer(301L, "intent-delete-terminal", "PENDING_DELETE");
    insertAnswer(302L, "intent-delete-active", "PENDING_DELETE");

    int updatedRows = adapter.reconcileTerminalDeleteRollbacks(100);

    assertThat(updatedRows).isEqualTo(1);
    assertThat(loadAnswerDeleteIntentId(301L)).isNull();
    assertThat(loadAnswerDeleteStatus(301L)).isNull();
    assertThat(loadAnswerDeleteFailureTerminalStatus(301L)).isEqualTo("EXPIRED");
    assertThat(loadAnswerDeleteFailureReason(301L)).isEqualTo("EXECUTION_INTENT_EXPIRED");
    assertThat(loadAnswerDeleteIntentId(302L)).isEqualTo("intent-delete-active");
    assertThat(loadAnswerDeleteStatus(302L)).isEqualTo("PENDING_DELETE");
  }

  private void createSchema() {
    jdbcTemplate.execute(
        """
        CREATE TABLE web3_execution_intents (
            public_id VARCHAR(100) PRIMARY KEY,
            resource_type VARCHAR(40) NOT NULL,
            action_type VARCHAR(60) NOT NULL,
            status VARCHAR(40) NOT NULL,
            last_error_reason VARCHAR(500)
        )
        """);
    jdbcTemplate.execute(
        """
        CREATE TABLE qna_answer_update_states (
            id BIGINT PRIMARY KEY,
            answer_id BIGINT NOT NULL,
            update_version BIGINT NOT NULL,
            execution_intent_public_id VARCHAR(100),
            status VARCHAR(40) NOT NULL,
            error_reason VARCHAR(500),
            updated_at TIMESTAMP
        )
        """);
    jdbcTemplate.execute(
        """
        CREATE TABLE answers (
            id BIGINT PRIMARY KEY,
            pending_delete_status VARCHAR(40),
            current_delete_execution_intent_id VARCHAR(100),
            delete_preparation_token VARCHAR(100),
            delete_preparation_expires_at TIMESTAMP,
            delete_failure_terminal_status VARCHAR(40),
            delete_failure_reason VARCHAR(500),
            updated_at TIMESTAMP
        )
        """);
  }

  private void insertExecutionIntent(
      String publicId, String resourceType, String actionType, String status, String errorReason) {
    jdbcTemplate.update(
        """
        INSERT INTO web3_execution_intents (
            public_id,
            resource_type,
            action_type,
            status,
            last_error_reason
        ) VALUES (?, ?, ?, ?, ?)
        """,
        publicId,
        resourceType,
        actionType,
        status,
        errorReason);
  }

  private void insertAnswerUpdateState(
      Long id, Long answerId, Long updateVersion, String executionIntentId, String status) {
    jdbcTemplate.update(
        """
        INSERT INTO qna_answer_update_states (
            id,
            answer_id,
            update_version,
            execution_intent_public_id,
            status,
            updated_at
        ) VALUES (?, ?, ?, ?, ?, NOW())
        """,
        id,
        answerId,
        updateVersion,
        executionIntentId,
        status);
  }

  private void insertAnswer(Long id, String executionIntentId, String pendingDeleteStatus) {
    jdbcTemplate.update(
        """
        INSERT INTO answers (
            id,
            pending_delete_status,
            current_delete_execution_intent_id,
            delete_preparation_token,
            delete_preparation_expires_at,
            updated_at
        ) VALUES (?, ?, ?, 'delete-token', NOW(), NOW())
        """,
        id,
        pendingDeleteStatus,
        executionIntentId);
  }

  private String loadAnswerUpdateStatus(Long stateId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM qna_answer_update_states WHERE id = ?", String.class, stateId);
  }

  private String loadAnswerUpdateErrorReason(Long stateId) {
    return jdbcTemplate.queryForObject(
        "SELECT error_reason FROM qna_answer_update_states WHERE id = ?", String.class, stateId);
  }

  private String loadAnswerDeleteIntentId(Long answerId) {
    return jdbcTemplate.queryForObject(
        "SELECT current_delete_execution_intent_id FROM answers WHERE id = ?",
        String.class,
        answerId);
  }

  private String loadAnswerDeleteStatus(Long answerId) {
    return jdbcTemplate.queryForObject(
        "SELECT pending_delete_status FROM answers WHERE id = ?", String.class, answerId);
  }

  private String loadAnswerDeleteFailureTerminalStatus(Long answerId) {
    return jdbcTemplate.queryForObject(
        "SELECT delete_failure_terminal_status FROM answers WHERE id = ?", String.class, answerId);
  }

  private String loadAnswerDeleteFailureReason(Long answerId) {
    return jdbcTemplate.queryForObject(
        "SELECT delete_failure_reason FROM answers WHERE id = ?", String.class, answerId);
  }
}
