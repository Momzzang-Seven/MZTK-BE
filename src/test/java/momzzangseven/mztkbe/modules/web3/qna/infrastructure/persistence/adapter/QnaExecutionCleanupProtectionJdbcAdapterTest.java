package momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@DisplayName("QnaExecutionCleanupProtectionJdbcAdapter")
class QnaExecutionCleanupProtectionJdbcAdapterTest {

  private JdbcTemplate jdbcTemplate;
  private QnaExecutionCleanupProtectionJdbcAdapter adapter;

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
    adapter =
        new QnaExecutionCleanupProtectionJdbcAdapter(new NamedParameterJdbcTemplate(dataSource));
    createSchema();
  }

  @Test
  @DisplayName("answer execution intent ref can be loaded by execution intent id")
  void findAnswerExecutionIntentRefLoadsRef() {
    insertAnswerIntentRef("intent-answer-ref", 101L, 202L, "QNA_ANSWER_SUBMIT");

    var result = adapter.findAnswerExecutionIntentRef("intent-answer-ref");

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().postId()).isEqualTo(101L);
    assertThat(result.orElseThrow().answerId()).isEqualTo(202L);
    assertThat(result.orElseThrow().actionType().name()).isEqualTo("QNA_ANSWER_SUBMIT");
  }

  @Test
  @DisplayName("protected answer update states are detected")
  void hasProtectedAnswerUpdateStateDetectsProtectedStatuses() {
    insertAnswerUpdateState("intent-answer-update", "RECONCILIATION_REQUIRED");
    insertAnswerUpdateState("intent-confirmed", "CONFIRMED");

    assertThat(adapter.hasProtectedAnswerUpdateState("intent-answer-update")).isTrue();
    assertThat(adapter.hasProtectedAnswerUpdateState("intent-confirmed")).isFalse();
    assertThat(adapter.hasProtectedAnswerUpdateState("missing")).isFalse();
  }

  private void createSchema() {
    jdbcTemplate.execute(
        """
        CREATE TABLE qna_answer_execution_intent_refs (
            execution_intent_public_id VARCHAR(100) NOT NULL,
            post_id BIGINT NOT NULL,
            answer_id BIGINT NOT NULL,
            status_snapshot VARCHAR(30),
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

  private void insertAnswerIntentRef(
      String executionIntentPublicId, Long postId, Long answerId, String actionType) {
    jdbcTemplate.update(
        """
        INSERT INTO qna_answer_execution_intent_refs (
            execution_intent_public_id,
            post_id,
            answer_id,
            status_snapshot,
            action_type
        ) VALUES (?, ?, ?, 'CONFIRMED', ?)
        """,
        executionIntentPublicId,
        postId,
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
