package momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionState;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.entity.QnaAnswerProjectionEntity;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.entity.QnaQuestionProjectionEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Qna auto-accept candidate query integration test")
class QnaAutoAcceptCandidateQueryTest {

  @Autowired private QnaAnswerProjectionJpaRepository qnaAnswerProjectionJpaRepository;
  @Autowired private QnaQuestionProjectionJpaRepository qnaQuestionProjectionJpaRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("claimNextAutoAcceptCandidate selects earliest active answer from answered question")
  void claimNextAutoAcceptCandidate_selectsEarliestActiveAnswer() {
    saveQuestion(101L, QnaQuestionState.ANSWERED.code());
    saveLocalQuestion(101L, 7L, "OPEN");
    saveAnswer(201L, 101L, false, at(9, 0));
    saveLocalAnswer(201L, 101L, 22L, false, at(9, 0));
    saveAnswer(202L, 101L, false, at(9, 5));
    saveLocalAnswer(202L, 101L, 23L, false, at(9, 5));

    var result =
        qnaAnswerProjectionJpaRepository.claimNextAutoAcceptCandidate(
            at(9, 30), QnaQuestionState.ANSWERED.code());

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().getPostId()).isEqualTo(101L);
    assertThat(result.orElseThrow().getAnswerId()).isEqualTo(201L);
  }

  @Test
  @DisplayName("claimNextAutoAcceptCandidate ignores non-answered questions and accepted answers")
  void claimNextAutoAcceptCandidate_ignoresIneligibleRows() {
    saveQuestion(101L, QnaQuestionState.CREATED.code());
    saveLocalQuestion(101L, 7L, "OPEN");
    saveAnswer(201L, 101L, false, at(9, 0));
    saveLocalAnswer(201L, 101L, 22L, false, at(9, 0));
    saveQuestion(102L, QnaQuestionState.ANSWERED.code());
    saveLocalQuestion(102L, 7L, "OPEN");
    saveAnswer(202L, 102L, true, at(8, 30));
    saveLocalAnswer(202L, 102L, 22L, true, at(8, 30));
    saveAnswer(203L, 102L, false, at(8, 40));
    saveLocalAnswer(203L, 102L, 23L, false, at(8, 40));

    var result =
        qnaAnswerProjectionJpaRepository.claimNextAutoAcceptCandidate(
            at(9, 30), QnaQuestionState.ANSWERED.code());

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().getPostId()).isEqualTo(102L);
    assertThat(result.orElseThrow().getAnswerId()).isEqualTo(203L);
  }

  @Test
  @DisplayName("claimNextAutoAcceptCandidate skips rows with active execution intents")
  void claimNextAutoAcceptCandidate_skipsRowsWithActiveExecutionIntents() {
    saveQuestion(101L, QnaQuestionState.ANSWERED.code());
    saveLocalQuestion(101L, 7L, "OPEN");
    saveAnswer(201L, 101L, false, at(9, 0));
    saveLocalAnswer(201L, 101L, 22L, false, at(9, 0));
    saveActiveIntent("QUESTION", "101", at(9, 1));

    saveQuestion(102L, QnaQuestionState.ANSWERED.code());
    saveLocalQuestion(102L, 8L, "OPEN");
    saveAnswer(202L, 102L, false, at(9, 5));
    saveLocalAnswer(202L, 102L, 23L, false, at(9, 5));

    var result =
        qnaAnswerProjectionJpaRepository.claimNextAutoAcceptCandidate(
            at(10, 0), QnaQuestionState.ANSWERED.code());

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().getPostId()).isEqualTo(102L);
    assertThat(result.orElseThrow().getAnswerId()).isEqualTo(202L);
  }

  @Test
  @DisplayName("claimNextAutoAcceptCandidate skips rows with blank local content")
  void claimNextAutoAcceptCandidate_skipsRowsWithBlankLocalContent() {
    saveQuestion(101L, QnaQuestionState.ANSWERED.code());
    saveLocalQuestion(101L, 7L, "OPEN", "   ");
    saveAnswer(201L, 101L, false, at(9, 0));
    saveLocalAnswer(201L, 101L, 22L, false, at(9, 0), "answer-201");

    saveQuestion(102L, QnaQuestionState.ANSWERED.code());
    saveLocalQuestion(102L, 8L, "OPEN");
    saveAnswer(202L, 102L, false, at(9, 5));
    saveLocalAnswer(202L, 102L, 23L, false, at(9, 5));

    var result =
        qnaAnswerProjectionJpaRepository.claimNextAutoAcceptCandidate(
            at(10, 0), QnaQuestionState.ANSWERED.code());

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().getPostId()).isEqualTo(102L);
    assertThat(result.orElseThrow().getAnswerId()).isEqualTo(202L);
  }

  private void saveQuestion(Long postId, int state) {
    qnaQuestionProjectionJpaRepository.save(
        QnaQuestionProjectionEntity.builder()
            .postId(postId)
            .questionId("0x" + "%064x".formatted(postId))
            .askerUserId(7L)
            .tokenAddress("0x" + "1".repeat(40))
            .rewardAmountWei(new BigInteger("50000000000000000000"))
            .questionHash("0x" + "a".repeat(64))
            .acceptedAnswerId("0x" + "0".repeat(64))
            .answerCount(1)
            .state(state)
            .createdAt(at(8, 0))
            .updatedAt(at(8, 0))
            .build());
  }

  private void saveAnswer(Long answerId, Long postId, boolean accepted, LocalDateTime createdAt) {
    qnaAnswerProjectionJpaRepository.save(
        QnaAnswerProjectionEntity.builder()
            .answerId(answerId)
            .postId(postId)
            .questionId("0x" + "%064x".formatted(postId))
            .answerKey("0x" + "%064x".formatted(answerId))
            .responderUserId(22L)
            .contentHash("0x" + "b".repeat(64))
            .accepted(accepted)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build());
  }

  private void saveLocalQuestion(Long postId, Long userId, String status) {
    saveLocalQuestion(postId, userId, status, "content-" + postId);
  }

  private void saveLocalQuestion(Long postId, Long userId, String status, String content) {
    jdbcTemplate.update(
        "insert into posts (id, user_id, type, title, content, reward, accepted_answer_id, status, created_at, updated_at) "
            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        postId,
        userId,
        "QUESTION",
        "title-" + postId,
        content,
        50L,
        null,
        status,
        Timestamp.valueOf(at(8, 0)),
        Timestamp.valueOf(at(8, 0)));
  }

  private void saveLocalAnswer(
      Long answerId, Long postId, Long userId, boolean accepted, LocalDateTime createdAt) {
    saveLocalAnswer(answerId, postId, userId, accepted, createdAt, "answer-" + answerId);
  }

  private void saveLocalAnswer(
      Long answerId,
      Long postId,
      Long userId,
      boolean accepted,
      LocalDateTime createdAt,
      String content) {
    jdbcTemplate.update(
        "insert into answers (id, post_id, user_id, content, is_accepted, created_at, updated_at) "
            + "values (?, ?, ?, ?, ?, ?, ?)",
        answerId,
        postId,
        userId,
        content,
        accepted,
        Timestamp.valueOf(createdAt),
        Timestamp.valueOf(createdAt));
  }

  private void saveActiveIntent(String resourceType, String resourceId, LocalDateTime createdAt) {
    jdbcTemplate.update(
        "insert into web3_execution_intents "
            + "(public_id, root_idempotency_key, attempt_no, resource_type, resource_id, action_type, requester_user_id, counterparty_user_id, mode, status, payload_hash, payload_snapshot_json, expires_at, unsigned_tx_snapshot, unsigned_tx_fingerprint, reserved_sponsor_cost_wei, sponsor_usage_date_kst, created_at, updated_at) "
            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        "intent-" + resourceType + "-" + resourceId,
        "root-" + resourceType + "-" + resourceId,
        1,
        resourceType,
        resourceId,
        "QNA_ADMIN_SETTLE",
        7L,
        22L,
        "EIP1559",
        "AWAITING_SIGNATURE",
        "0x" + "a".repeat(64),
        "{\"payload\":true}",
        Timestamp.valueOf(createdAt.plusMinutes(5)),
        "{\"chainId\":11155111}",
        "0x" + "b".repeat(64),
        BigInteger.ZERO,
        java.sql.Date.valueOf("2026-04-17"),
        Timestamp.valueOf(createdAt),
        Timestamp.valueOf(createdAt));
  }

  private LocalDateTime at(int hour, int minute) {
    return LocalDateTime.of(2026, 4, 17, hour, minute);
  }
}
