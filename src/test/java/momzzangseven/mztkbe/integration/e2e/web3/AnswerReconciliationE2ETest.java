package momzzangseven.mztkbe.integration.e2e.web3;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import momzzangseven.mztkbe.integration.e2e.support.E2ETestBase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.answer.application.port.in.ReconcileAnswerPublicationUseCase;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteS3ObjectPort;
import momzzangseven.mztkbe.modules.post.application.port.out.QuestionLifecycleExecutionPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaContentHashFactory;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaEscrowIdCodec;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(
    properties = {
      "web3.chain-id=1337",
      "web3.eip712.chain-id=1337",
      "web3.eip7702.enabled=false",
      "web3.reward-token.enabled=false"
    })
@DisplayName("[E2E] Answer publication reconciliation")
class AnswerReconciliationE2ETest extends E2ETestBase {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ReconcileAnswerPublicationUseCase reconcileAnswerPublicationUseCase;

  @MockitoBean private KakaoAuthPort kakaoAuthPort;
  @MockitoBean private GoogleAuthPort googleAuthPort;
  @MockitoBean private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;
  @MockitoBean private DeleteS3ObjectPort deleteS3ObjectPort;
  @MockitoBean private QuestionLifecycleExecutionPort questionLifecycleExecutionPort;

  @Test
  @DisplayName("reconcile uses PostgreSQL SQL paths for answer lifecycle terminal states")
  void reconcileAnswerPublicationUsesPostgresSqlPaths() {
    TestUser asker = signupAndLogin("reconcile-questioner");
    TestUser answerer = signupAndLogin("reconcile-answerer");
    Long postId = insertQuestionPost(asker.userId(), "reconcile question", "question body", 50L);
    insertQuestionProjection(postId, asker.userId(), "question body", 50L, 99);

    Long confirmedSubmitAnswerId =
        insertAnswer(
            postId, answerer.userId(), "confirmed submit", "PENDING", "submit-ok", null, null);
    insertAnswerProjection(confirmedSubmitAnswerId, postId, answerer.userId(), "confirmed submit");
    Long failedSubmitAnswerId =
        insertAnswer(
            postId, answerer.userId(), "failed submit", "PENDING", "submit-fail", null, null);
    Long confirmedUpdateAnswerId =
        insertAnswer(postId, answerer.userId(), "before update", "VISIBLE", null, null, null);
    Long failedUpdateAnswerId =
        insertAnswer(postId, answerer.userId(), "failed update", "VISIBLE", null, null, null);
    Long confirmedDeleteAnswerId =
        insertAnswer(
            postId, answerer.userId(), "confirmed delete", "VISIBLE", null, "PENDING", "delete-ok");
    Long rolledBackDeleteAnswerId =
        insertAnswer(
            postId,
            answerer.userId(),
            "rolled back delete",
            "VISIBLE",
            null,
            "PENDING",
            "delete-fail");

    insertExecutionIntent(
        "submit-ok",
        confirmedSubmitAnswerId,
        "QNA_ANSWER_SUBMIT",
        "CONFIRMED",
        answerer.userId(),
        null);
    insertExecutionIntent(
        "submit-fail",
        failedSubmitAnswerId,
        "QNA_ANSWER_SUBMIT",
        "FAILED_ONCHAIN",
        answerer.userId(),
        "RPC_UNAVAILABLE");
    insertExecutionIntent(
        "update-ok",
        confirmedUpdateAnswerId,
        "QNA_ANSWER_UPDATE",
        "CONFIRMED",
        answerer.userId(),
        null);
    insertExecutionIntent(
        "update-fail",
        failedUpdateAnswerId,
        "QNA_ANSWER_UPDATE",
        "FAILED_ONCHAIN",
        answerer.userId(),
        "RPC_UNAVAILABLE");
    insertExecutionIntent(
        "delete-ok",
        confirmedDeleteAnswerId,
        "QNA_ANSWER_DELETE",
        "CONFIRMED",
        answerer.userId(),
        null);
    insertExecutionIntent(
        "delete-fail",
        rolledBackDeleteAnswerId,
        "QNA_ANSWER_DELETE",
        "EXPIRED",
        answerer.userId(),
        "EXECUTION_INTENT_EXPIRED");
    insertAnswerUpdateState(
        confirmedUpdateAnswerId, 1L, "update-ok", "INTENT_BOUND", "after update");
    insertAnswerUpdateState(failedUpdateAnswerId, 1L, "update-fail", "INTENT_BOUND", "ignored");

    var result = reconcileAnswerPublicationUseCase.reconcile(100);

    assertThat(result.confirmedSubmits()).isEqualTo(1);
    assertThat(result.terminalSubmitFailures()).isEqualTo(1);
    assertThat(result.confirmedUpdates()).isEqualTo(1);
    assertThat(result.terminalUpdateFailures()).isEqualTo(1);
    assertThat(result.confirmedDeletes()).isEqualTo(1);
    assertThat(result.terminalDeleteRollbacks()).isEqualTo(1);
    assertThat(loadAnswerRow(confirmedSubmitAnswerId).get("publication_status"))
        .isEqualTo("VISIBLE");
    assertThat(loadAnswerRow(confirmedSubmitAnswerId).get("current_create_execution_intent_id"))
        .isNull();
    assertThat(loadAnswerRow(failedSubmitAnswerId).get("publication_status")).isEqualTo("FAILED");
    assertThat(loadAnswerContent(confirmedUpdateAnswerId)).isEqualTo("after update");
    assertThat(loadAnswerUpdateStatus(confirmedUpdateAnswerId)).isEqualTo("CONFIRMED");
    assertThat(loadAnswerUpdateStatus(failedUpdateAnswerId)).isEqualTo("FAILED");
    assertThat(countAnswersById(confirmedDeleteAnswerId)).isZero();
    assertThat(loadAnswerRow(rolledBackDeleteAnswerId).get("pending_delete_status")).isNull();
    assertThat(loadQuestionAnswerCount(postId)).isEqualTo(1);
  }

  private Long insertQuestionPost(Long userId, String title, String content, Long reward) {
    Instant now = Instant.now();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        conn -> {
          PreparedStatement ps =
              conn.prepareStatement(
                  "INSERT INTO posts "
                      + "(user_id, type, title, content, reward, status, publication_status, moderation_status, created_at, updated_at) "
                      + "VALUES (?, 'QUESTION', ?, ?, ?, 'OPEN', 'VISIBLE', 'NORMAL', ?, ?)",
                  new String[] {"id"});
          ps.setLong(1, userId);
          ps.setString(2, title);
          ps.setString(3, content);
          ps.setLong(4, reward);
          ps.setTimestamp(5, Timestamp.from(now));
          ps.setTimestamp(6, Timestamp.from(now));
          return ps;
        },
        keyHolder);
    return requireGeneratedId(keyHolder);
  }

  private Long insertAnswer(
      Long postId,
      Long userId,
      String content,
      String publicationStatus,
      String createIntentId,
      String pendingDeleteStatus,
      String deleteIntentId) {
    Instant now = Instant.now();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        conn -> {
          PreparedStatement ps =
              conn.prepareStatement(
                  "INSERT INTO answers "
                      + "(post_id, user_id, content, is_accepted, publication_status, current_create_execution_intent_id, "
                      + "pending_delete_status, current_delete_execution_intent_id, created_at, updated_at) "
                      + "VALUES (?, ?, ?, false, ?, ?, ?, ?, ?, ?)",
                  new String[] {"id"});
          ps.setLong(1, postId);
          ps.setLong(2, userId);
          ps.setString(3, content);
          ps.setString(4, publicationStatus);
          ps.setString(5, createIntentId);
          ps.setString(6, pendingDeleteStatus);
          ps.setString(7, deleteIntentId);
          ps.setTimestamp(8, Timestamp.from(now));
          ps.setTimestamp(9, Timestamp.from(now));
          return ps;
        },
        keyHolder);
    return requireGeneratedId(keyHolder);
  }

  private void insertQuestionProjection(
      Long postId, Long askerUserId, String content, Long rewardAmount, int answerCount) {
    Instant now = Instant.now();
    jdbcTemplate.update(
        "INSERT INTO web3_qna_questions "
            + "(post_id, question_id, asker_user_id, token_address, reward_amount_wei, "
            + "question_hash, accepted_answer_id, answer_count, state, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        postId,
        QnaEscrowIdCodec.questionId(postId),
        askerUserId,
        "0x" + "1".repeat(40),
        BigInteger.valueOf(rewardAmount),
        QnaContentHashFactory.hash(content),
        QnaEscrowIdCodec.zeroBytes32(),
        answerCount,
        1000,
        Timestamp.from(now),
        Timestamp.from(now));
  }

  private void insertAnswerProjection(
      Long answerId, Long postId, Long responderUserId, String content) {
    Instant now = Instant.now();
    jdbcTemplate.update(
        "INSERT INTO web3_qna_answers "
            + "(answer_id, post_id, question_id, answer_key, responder_user_id, content_hash, accepted, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, false, ?, ?)",
        answerId,
        postId,
        QnaEscrowIdCodec.questionId(postId),
        QnaEscrowIdCodec.answerId(answerId),
        responderUserId,
        QnaContentHashFactory.hash(content),
        Timestamp.from(now),
        Timestamp.from(now));
  }

  private void insertExecutionIntent(
      String publicId,
      Long answerId,
      String actionType,
      String status,
      Long requesterUserId,
      String lastErrorReason) {
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        INSERT INTO web3_execution_intents (
            public_id,
            root_idempotency_key,
            attempt_no,
            resource_type,
            resource_id,
            action_type,
            requester_user_id,
            mode,
            status,
            payload_hash,
            expires_at,
            reserved_sponsor_cost_wei,
            sponsor_usage_date_kst,
            last_error_reason,
            created_at,
            updated_at
        ) VALUES (?, ?, 1, 'ANSWER', ?, ?, ?, 'EIP7702', ?, ?, ?, 0, ?, ?, ?, ?)
        """,
        publicId,
        "root-" + publicId,
        answerId.toString(),
        actionType,
        requesterUserId,
        status,
        "0x" + "0".repeat(64),
        Timestamp.from(now.plusSeconds(3600)),
        LocalDate.now(),
        lastErrorReason,
        Timestamp.from(now),
        Timestamp.from(now));
  }

  private void insertAnswerUpdateState(
      Long answerId, Long updateVersion, String executionIntentId, String status, String content) {
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        INSERT INTO qna_answer_update_states (
            answer_id,
            update_version,
            update_token,
            execution_intent_public_id,
            status,
            pending_content,
            pending_image_update,
            created_at,
            updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, false, ?, ?)
        """,
        answerId,
        updateVersion,
        "token-" + executionIntentId,
        executionIntentId,
        status,
        content,
        Timestamp.from(now),
        Timestamp.from(now));
  }

  private Map<String, Object> loadAnswerRow(Long answerId) {
    return jdbcTemplate.queryForMap("SELECT * FROM answers WHERE id = ?", answerId);
  }

  private String loadAnswerContent(Long answerId) {
    return jdbcTemplate.queryForObject(
        "SELECT content FROM answers WHERE id = ?", String.class, answerId);
  }

  private String loadAnswerUpdateStatus(Long answerId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM qna_answer_update_states WHERE answer_id = ?", String.class, answerId);
  }

  private int countAnswersById(Long answerId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM answers WHERE id = ?", Integer.class, answerId);
    return count == null ? 0 : count;
  }

  private int loadQuestionAnswerCount(Long postId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT answer_count FROM web3_qna_questions WHERE post_id = ?", Integer.class, postId);
    return count == null ? 0 : count;
  }

  private Long requireGeneratedId(KeyHolder keyHolder) {
    Number generatedKey = keyHolder.getKey();
    if (generatedKey == null) {
      throw new IllegalStateException("Failed to insert test row");
    }
    return generatedKey.longValue();
  }
}
