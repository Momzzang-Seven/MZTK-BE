package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationStatePort.DeleteCandidate;
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
  @DisplayName("pending submit candidates include only pending answers with current create intent")
  void findPendingSubmitCandidatesRequiresPendingPublicationStatus() {
    insertAnswerWithPublication(101L, "PENDING", null, "intent-submit-pending", "pending");
    insertAnswerWithPublication(102L, "FAILED", null, "intent-submit-failed", "failed");
    insertAnswerWithPublication(
        103L, "RECONCILIATION_REQUIRED", null, "intent-submit-required", "required");
    insertAnswerWithPublication(104L, "VISIBLE", null, "intent-submit-visible", "visible");
    insertAnswerWithPublication(105L, "PENDING", null, null, "without-intent");

    assertThat(adapter.findPendingSubmitCandidates(10))
        .extracting("answerId")
        .containsExactly(101L);
  }

  @Test
  @DisplayName("submit confirmation transitions only current pending answers")
  void confirmSubmitIfCurrentRequiresPendingPublicationStatus() {
    insertAnswerWithPublication(111L, "PENDING", null, "intent-submit", "pending");
    insertAnswerWithPublication(112L, "FAILED", null, "intent-submit", "failed");

    assertThat(adapter.confirmSubmitIfCurrent(111L, "intent-submit")).isEqualTo(1);
    assertThat(adapter.confirmSubmitIfCurrent(112L, "intent-submit")).isZero();
    assertThat(loadPublicationStatus(111L)).isEqualTo("VISIBLE");
    assertThat(loadPublicationStatus(112L)).isEqualTo("FAILED");
  }

  @Test
  @DisplayName("submit failure transitions only current pending answers")
  void failSubmitIfCurrentRequiresPendingPublicationStatus() {
    insertAnswerWithPublication(121L, "PENDING", null, "intent-submit", "pending");
    insertAnswerWithPublication(122L, "RECONCILIATION_REQUIRED", null, "intent-submit", "required");

    assertThat(adapter.failSubmitIfCurrent(121L, "intent-submit", "EXPIRED", "timeout"))
        .isEqualTo(1);
    assertThat(adapter.failSubmitIfCurrent(122L, "intent-submit", "EXPIRED", "timeout")).isZero();
    assertThat(loadPublicationStatus(121L)).isEqualTo("FAILED");
    assertThat(loadPublicationStatus(122L)).isEqualTo("RECONCILIATION_REQUIRED");
  }

  @Test
  @DisplayName("intent bound update candidates require visible answers that are not pending delete")
  void findIntentBoundUpdateCandidatesRequiresPubliclyMutableAnswer() {
    insertAnswerWithPublication(131L, "VISIBLE", null, null, "visible");
    insertAnswerWithPublication(132L, "FAILED", null, null, "failed");
    insertAnswerWithPublication(133L, "VISIBLE", "PENDING", null, "deleting");
    insertAnswerUpdateState(11L, 131L, 1L, "intent-update-visible", "INTENT_BOUND");
    insertAnswerUpdateState(12L, 132L, 1L, "intent-update-failed", "INTENT_BOUND");
    insertAnswerUpdateState(13L, 133L, 1L, "intent-update-deleting", "INTENT_BOUND");

    assertThat(adapter.findIntentBoundUpdateCandidates(10))
        .extracting("answerId")
        .containsExactly(131L);
  }

  @Test
  @DisplayName(
      "confirmed update content applies only to visible answers that are not pending delete")
  void applyConfirmedUpdateContentIfCurrentRequiresPubliclyMutableAnswer() {
    insertAnswerWithPublication(141L, "VISIBLE", null, null, "old-visible");
    insertAnswerWithPublication(142L, "FAILED", null, null, "old-failed");
    insertAnswerWithPublication(143L, "VISIBLE", "PENDING", null, "old-deleting");
    insertAnswerUpdateState(21L, 141L, 1L, "intent-update", "INTENT_BOUND");
    insertAnswerUpdateState(22L, 142L, 1L, "intent-update", "INTENT_BOUND");
    insertAnswerUpdateState(23L, 143L, 1L, "intent-update", "INTENT_BOUND");

    assertThat(adapter.applyConfirmedUpdateContentIfCurrent(21L, 141L, "intent-update", "new"))
        .isEqualTo(1);
    assertThat(adapter.applyConfirmedUpdateContentIfCurrent(22L, 142L, "intent-update", "new"))
        .isZero();
    assertThat(adapter.applyConfirmedUpdateContentIfCurrent(23L, 143L, "intent-update", "new"))
        .isZero();
    assertThat(loadAnswerContent(141L)).isEqualTo("new");
    assertThat(loadAnswerContent(142L)).isEqualTo("old-failed");
    assertThat(loadAnswerContent(143L)).isEqualTo("old-deleting");
  }

  @Test
  @DisplayName("terminal answer update marks the current bound update state as failed")
  void failUpdateIfCurrentMarksIntentBoundStateFailed() {
    insertAnswerWithPublication(201L, "VISIBLE", null, null, "answer-201");
    insertAnswerWithPublication(202L, "VISIBLE", null, null, "answer-202");
    insertAnswerUpdateState(1L, 201L, 1L, "intent-update-terminal", "INTENT_BOUND");
    insertAnswerUpdateState(2L, 202L, 1L, "intent-update-active", "INTENT_BOUND");

    int updatedRows =
        adapter.failUpdateIfCurrent(
            1L, "intent-update-terminal", "FAILED_ONCHAIN", "RPC_UNAVAILABLE");

    assertThat(updatedRows).isEqualTo(1);
    assertThat(loadAnswerUpdateStatus(1L)).isEqualTo("FAILED");
    assertThat(loadAnswerUpdateErrorReason(1L)).isEqualTo("RPC_UNAVAILABLE");
    assertThat(loadAnswerUpdateStatus(2L)).isEqualTo("INTENT_BOUND");
  }

  @Test
  @DisplayName("terminal answer delete clears the current pending delete state")
  void rollbackDeleteIfCurrentClearsPendingDeleteState() {
    insertAnswer(301L, "intent-delete-terminal", "PENDING");
    insertAnswer(302L, "intent-delete-active", "PENDING");

    int updatedRows =
        adapter.rollbackDeleteIfCurrent(
            301L, "intent-delete-terminal", "EXPIRED", "EXECUTION_INTENT_EXPIRED");

    assertThat(updatedRows).isEqualTo(1);
    assertThat(loadAnswerDeleteIntentId(301L)).isNull();
    assertThat(loadAnswerDeleteStatus(301L)).isNull();
    assertThat(loadAnswerDeleteFailureTerminalStatus(301L)).isEqualTo("EXPIRED");
    assertThat(loadAnswerDeleteFailureReason(301L)).isEqualTo("EXECUTION_INTENT_EXPIRED");
    assertThat(loadAnswerDeleteIntentId(302L)).isEqualTo("intent-delete-active");
    assertThat(loadAnswerDeleteStatus(302L)).isEqualTo("PENDING");
  }

  @Test
  @DisplayName("confirmed answer delete requires matching current intent and pending status")
  void deleteConfirmedDeleteAnswerRequiresCurrentIntentAndPendingStatus() {
    insertAnswer(401L, "intent-delete", "PENDING");
    insertAnswer(402L, "intent-other", "PENDING");
    insertAnswer(403L, "intent-delete", "PREPARING");

    Long deleted = adapter.deleteConfirmedDeleteAnswer(new DeleteCandidate(401L, "intent-delete"));
    Long staleIntentMiss =
        adapter.deleteConfirmedDeleteAnswer(new DeleteCandidate(402L, "intent-delete"));
    Long staleStatusMiss =
        adapter.deleteConfirmedDeleteAnswer(new DeleteCandidate(403L, "intent-delete"));

    assertThat(deleted).isEqualTo(401L);
    assertThat(staleIntentMiss).isNull();
    assertThat(staleStatusMiss).isNull();
    assertThat(answerExists(401L)).isFalse();
    assertThat(answerExists(402L)).isTrue();
    assertThat(answerExists(403L)).isTrue();
  }

  private void createSchema() {
    jdbcTemplate.execute(
        """
        CREATE TABLE qna_answer_update_states (
            id BIGINT PRIMARY KEY,
            answer_id BIGINT NOT NULL,
            update_version BIGINT NOT NULL,
            execution_intent_public_id VARCHAR(100),
            pending_content VARCHAR(1000),
            status VARCHAR(40) NOT NULL,
            error_code VARCHAR(120),
            error_reason VARCHAR(500),
            updated_at TIMESTAMP
        )
        """);
    jdbcTemplate.execute(
        """
        CREATE TABLE answers (
            id BIGINT PRIMARY KEY,
            user_id BIGINT NOT NULL,
            content VARCHAR(1000),
            publication_status VARCHAR(40) NOT NULL,
            current_create_execution_intent_id VARCHAR(100),
            create_preparation_token VARCHAR(100),
            create_preparation_expires_at TIMESTAMP,
            publication_failure_terminal_status VARCHAR(40),
            publication_failure_reason VARCHAR(500),
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

  private void insertAnswerUpdateState(
      Long id, Long answerId, Long updateVersion, String executionIntentId, String status) {
    jdbcTemplate.update(
        """
        INSERT INTO qna_answer_update_states (
            id,
            answer_id,
            update_version,
            execution_intent_public_id,
            pending_content,
            status,
            updated_at
        ) VALUES (?, ?, ?, ?, 'pending-content', ?, NOW())
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
            user_id,
            content,
            publication_status,
            pending_delete_status,
            current_delete_execution_intent_id,
            delete_preparation_token,
            delete_preparation_expires_at,
            updated_at
        ) VALUES (?, 1, 'answer', 'VISIBLE', ?, ?, 'delete-token', NOW(), NOW())
        """,
        id,
        pendingDeleteStatus,
        executionIntentId);
  }

  private void insertAnswerWithPublication(
      Long id,
      String publicationStatus,
      String pendingDeleteStatus,
      String currentCreateExecutionIntentId,
      String content) {
    jdbcTemplate.update(
        """
        INSERT INTO answers (
            id,
            user_id,
            content,
            publication_status,
            current_create_execution_intent_id,
            create_preparation_token,
            create_preparation_expires_at,
            pending_delete_status,
            updated_at
        ) VALUES (?, 1, ?, ?, ?, 'create-token', NOW(), ?, NOW())
        """,
        id,
        content,
        publicationStatus,
        currentCreateExecutionIntentId,
        pendingDeleteStatus);
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

  private String loadPublicationStatus(Long answerId) {
    return jdbcTemplate.queryForObject(
        "SELECT publication_status FROM answers WHERE id = ?", String.class, answerId);
  }

  private String loadAnswerContent(Long answerId) {
    return jdbcTemplate.queryForObject(
        "SELECT content FROM answers WHERE id = ?", String.class, answerId);
  }

  private boolean answerExists(Long answerId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM answers WHERE id = ?", Integer.class, answerId);
    return count != null && count > 0;
  }
}
