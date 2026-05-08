package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.qna;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@DisplayName("QnaExecutionCleanupProtectionAdapter")
class QnaExecutionCleanupProtectionAdapterTest {

  private JdbcTemplate jdbcTemplate;
  private QnaExecutionCleanupProtectionAdapter adapter;

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
    adapter = new QnaExecutionCleanupProtectionAdapter(new NamedParameterJdbcTemplate(dataSource));
    createSchema();
  }

  @Test
  @DisplayName("failed question create recovery evidence is not deletable")
  void filterDeletableFinalizedIntentIdsProtectsFailedQuestionCreateIntent() {
    insertPost(101L, 7L, "QUESTION", "FAILED");
    insertPost(102L, 7L, "QUESTION", "VISIBLE");
    insertIntent(1L, "intent-protected", "QUESTION", "101", "QNA_QUESTION_CREATE", 7L);
    insertIntent(2L, "intent-visible-post", "QUESTION", "102", "QNA_QUESTION_CREATE", 7L);
    insertIntent(3L, "intent-requester-mismatch", "QUESTION", "101", "QNA_QUESTION_CREATE", 8L);
    insertIntent(4L, "intent-answer", "ANSWER", "201", "QNA_ANSWER_SUBMIT", 22L);

    List<Long> result = adapter.filterDeletableFinalizedIntentIds(List.of(1L, 2L, 3L, 4L));

    assertThat(result).containsExactlyInAnyOrder(2L, 3L, 4L);
  }

  @Test
  @DisplayName("answer lifecycle recovery evidence is not deletable")
  void filterDeletableFinalizedIntentIdsProtectsAnswerLifecycleIntent() {
    insertAnswer(201L, "PENDING", "intent-answer-create", null);
    insertAnswer(202L, "FAILED", null, null);
    insertAnswer(203L, "VISIBLE", null, "intent-answer-delete");
    insertIntent(5L, "intent-answer-create", "ANSWER", "201", "QNA_ANSWER_SUBMIT", 22L);
    insertIntent(6L, "intent-answer-ref", "ANSWER", "202", "QNA_ANSWER_SUBMIT", 22L);
    insertIntent(7L, "intent-answer-delete", "ANSWER", "203", "QNA_ANSWER_DELETE", 22L);
    insertIntent(8L, "intent-answer-update", "ANSWER", "204", "QNA_ANSWER_UPDATE", 22L);
    insertIntent(9L, "intent-free", "ANSWER", "205", "QNA_ANSWER_SUBMIT", 22L);
    insertAnswerIntentRef("intent-answer-ref", 202L, "QNA_ANSWER_SUBMIT");
    insertAnswerUpdateState("intent-answer-update", "RECONCILIATION_REQUIRED");

    List<Long> result = adapter.filterDeletableFinalizedIntentIds(List.of(5L, 6L, 7L, 8L, 9L));

    assertThat(result).containsExactly(9L);
  }

  @Test
  @DisplayName("empty candidates short-circuit")
  void filterDeletableFinalizedIntentIdsReturnsEmptyForEmptyCandidates() {
    assertThat(adapter.filterDeletableFinalizedIntentIds(List.of())).isEmpty();
  }

  private void createSchema() {
    jdbcTemplate.execute(
        """
        CREATE TABLE web3_execution_intents (
            id BIGINT PRIMARY KEY,
            public_id VARCHAR(100) NOT NULL,
            resource_type VARCHAR(40) NOT NULL,
            resource_id VARCHAR(250) NOT NULL,
            action_type VARCHAR(60) NOT NULL,
            requester_user_id BIGINT NOT NULL
        )
        """);
    jdbcTemplate.execute(
        """
        CREATE TABLE posts (
            id BIGINT PRIMARY KEY,
            user_id BIGINT NOT NULL,
            type VARCHAR(20) NOT NULL,
            publication_status VARCHAR(20) NOT NULL
        )
        """);
    jdbcTemplate.execute(
        """
        CREATE TABLE answers (
            id BIGINT PRIMARY KEY,
            publication_status VARCHAR(32),
            current_create_execution_intent_id VARCHAR(100),
            current_delete_execution_intent_id VARCHAR(100)
        )
        """);
    jdbcTemplate.execute(
        """
        CREATE TABLE qna_answer_execution_intent_refs (
            execution_intent_public_id VARCHAR(100) NOT NULL,
            answer_id BIGINT NOT NULL,
            action_type VARCHAR(60) NOT NULL
        )
        """);
    jdbcTemplate.execute(
        """
        CREATE TABLE qna_answer_update_states (
            execution_intent_public_id VARCHAR(100),
            status VARCHAR(40) NOT NULL
        )
        """);
  }

  private void insertPost(Long id, Long userId, String type, String publicationStatus) {
    jdbcTemplate.update(
        "INSERT INTO posts (id, user_id, type, publication_status) VALUES (?, ?, ?, ?)",
        id,
        userId,
        type,
        publicationStatus);
  }

  private void insertAnswer(
      Long id,
      String publicationStatus,
      String currentCreateIntentId,
      String currentDeleteIntentId) {
    jdbcTemplate.update(
        """
        INSERT INTO answers (
            id,
            publication_status,
            current_create_execution_intent_id,
            current_delete_execution_intent_id
        ) VALUES (?, ?, ?, ?)
        """,
        id,
        publicationStatus,
        currentCreateIntentId,
        currentDeleteIntentId);
  }

  private void insertIntent(
      Long id,
      String publicId,
      String resourceType,
      String resourceId,
      String actionType,
      Long requesterUserId) {
    jdbcTemplate.update(
        """
        INSERT INTO web3_execution_intents (
            id,
            public_id,
            resource_type,
            resource_id,
            action_type,
            requester_user_id
        ) VALUES (?, ?, ?, ?, ?, ?)
        """,
        id,
        publicId,
        resourceType,
        resourceId,
        actionType,
        requesterUserId);
  }

  private void insertAnswerIntentRef(
      String executionIntentPublicId, Long answerId, String actionType) {
    jdbcTemplate.update(
        """
        INSERT INTO qna_answer_execution_intent_refs (
            execution_intent_public_id,
            answer_id,
            action_type
        ) VALUES (?, ?, ?)
        """,
        executionIntentPublicId,
        answerId,
        actionType);
  }

  private void insertAnswerUpdateState(String executionIntentPublicId, String status) {
    jdbcTemplate.update(
        """
        INSERT INTO qna_answer_update_states (
            execution_intent_public_id,
            status
        ) VALUES (?, ?)
        """,
        executionIntentPublicId,
        status);
  }
}
