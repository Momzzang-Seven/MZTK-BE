package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.adapter;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationStatePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerPublicationReconciliationJdbcAdapter
    implements AnswerPublicationReconciliationStatePort {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public boolean tryAcquireReconciliationLock() {
    Boolean acquired =
        jdbcTemplate.queryForObject(
            "SELECT pg_try_advisory_xact_lock(hashtext('mztk.answer.publication.reconciliation'))",
            Boolean.class);
    return Boolean.TRUE.equals(acquired);
  }

  @Override
  public List<CreateCandidate> findPendingSubmitCandidates(int batchSize) {
    return jdbcTemplate.query(
        """
        SELECT id, current_create_execution_intent_id
        FROM answers
        WHERE publication_status = 'PENDING'
          AND current_create_execution_intent_id IS NOT NULL
        ORDER BY id
        LIMIT ?
        """,
        (rs, rowNum) ->
            new CreateCandidate(
                rs.getLong("id"), rs.getString("current_create_execution_intent_id")),
        batchSize);
  }

  @Override
  public int confirmSubmitIfCurrent(Long answerId, String executionIntentId) {
    return jdbcTemplate.update(
        """
        UPDATE answers
        SET publication_status = 'VISIBLE',
            current_create_execution_intent_id = NULL,
            create_preparation_token = NULL,
            create_preparation_expires_at = NULL,
            publication_failure_terminal_status = NULL,
            publication_failure_reason = NULL,
            updated_at = NOW()
        WHERE id = ?
          AND current_create_execution_intent_id = ?
          AND publication_status = 'PENDING'
        """,
        answerId,
        executionIntentId);
  }

  @Override
  public int failSubmitIfCurrent(
      Long answerId, String executionIntentId, String terminalStatus, String failureReason) {
    return jdbcTemplate.update(
        """
        UPDATE answers
        SET publication_status = 'FAILED',
            current_create_execution_intent_id = NULL,
            create_preparation_token = NULL,
            create_preparation_expires_at = NULL,
            publication_failure_terminal_status = ?,
            publication_failure_reason = ?,
            updated_at = NOW()
        WHERE id = ?
          AND current_create_execution_intent_id = ?
          AND publication_status = 'PENDING'
        """,
        terminalStatus,
        failureReason,
        answerId,
        executionIntentId);
  }

  @Override
  public List<UpdateCandidate> findIntentBoundUpdateCandidates(int batchSize) {
    return jdbcTemplate.query(
        """
        SELECT s.id, s.answer_id, a.user_id, s.execution_intent_public_id, s.pending_content
        FROM qna_answer_update_states s
        JOIN answers a
          ON a.id = s.answer_id
        WHERE s.status = 'INTENT_BOUND'
          AND s.execution_intent_public_id IS NOT NULL
          AND a.publication_status = 'VISIBLE'
          AND a.pending_delete_status IS NULL
        ORDER BY s.answer_id, s.update_version
        LIMIT ?
        """,
        (rs, rowNum) ->
            new UpdateCandidate(
                rs.getLong("id"),
                rs.getLong("answer_id"),
                rs.getLong("user_id"),
                rs.getString("execution_intent_public_id"),
                rs.getString("pending_content")),
        batchSize);
  }

  @Override
  public int applyConfirmedUpdateContentIfCurrent(
      Long stateId, Long answerId, String executionIntentId, String pendingContent) {
    return jdbcTemplate.update(
        """
        UPDATE answers a
        SET content = ?,
            updated_at = NOW()
        WHERE a.id = ?
          AND a.publication_status = 'VISIBLE'
          AND a.pending_delete_status IS NULL
          AND EXISTS (
              SELECT 1
              FROM qna_answer_update_states s
              WHERE s.id = ?
                AND s.answer_id = a.id
                AND s.execution_intent_public_id = ?
                AND s.status = 'INTENT_BOUND'
          )
        """,
        pendingContent,
        answerId,
        stateId,
        executionIntentId);
  }

  @Override
  public int markUpdateConfirmedIfCurrent(Long stateId, String executionIntentId) {
    return jdbcTemplate.update(
        """
        UPDATE qna_answer_update_states
        SET status = 'CONFIRMED',
            updated_at = NOW()
        WHERE id = ?
          AND execution_intent_public_id = ?
          AND status = 'INTENT_BOUND'
        """,
        stateId,
        executionIntentId);
  }

  @Override
  public int failUpdateIfCurrent(
      Long stateId, String executionIntentId, String terminalStatus, String failureReason) {
    return jdbcTemplate.update(
        """
        UPDATE qna_answer_update_states
        SET status = 'FAILED',
            error_code = ?,
            error_reason = ?,
            updated_at = NOW()
        WHERE id = ?
          AND execution_intent_public_id = ?
          AND status = 'INTENT_BOUND'
        """,
        terminalStatus,
        failureReason,
        stateId,
        executionIntentId);
  }

  @Override
  public int markUpdateReconciliationRequiredIfCurrent(
      Long stateId, String executionIntentId, String reason) {
    return jdbcTemplate.update(
        """
        UPDATE qna_answer_update_states
        SET status = 'RECONCILIATION_REQUIRED',
            reconciliation_required_reason = ?,
            updated_at = NOW()
        WHERE id = ?
          AND execution_intent_public_id = ?
          AND status = 'INTENT_BOUND'
        """,
        reason,
        stateId,
        executionIntentId);
  }

  @Override
  public List<DeleteCandidate> findPendingDeleteCandidates(int batchSize) {
    return jdbcTemplate.query(
        """
        SELECT id, current_delete_execution_intent_id
        FROM answers
        WHERE current_delete_execution_intent_id IS NOT NULL
          AND pending_delete_status = 'PENDING'
        ORDER BY id
        LIMIT ?
        """,
        (rs, rowNum) ->
            new DeleteCandidate(
                rs.getLong("id"), rs.getString("current_delete_execution_intent_id")),
        batchSize);
  }

  @Override
  public Long deleteConfirmedDeleteAnswer(DeleteCandidate candidate) {
    if (candidate == null
        || candidate.answerId() == null
        || candidate.executionIntentId() == null) {
      return null;
    }
    int deletedRows =
        jdbcTemplate.update(
            """
            DELETE FROM answers
            WHERE id = ?
              AND current_delete_execution_intent_id = ?
              AND pending_delete_status = 'PENDING'
            """,
            candidate.answerId(),
            candidate.executionIntentId());
    return deletedRows == 1 ? candidate.answerId() : null;
  }

  @Override
  public int rollbackDeleteIfCurrent(
      Long answerId, String executionIntentId, String terminalStatus, String failureReason) {
    return jdbcTemplate.update(
        """
        UPDATE answers
        SET pending_delete_status = NULL,
            current_delete_execution_intent_id = NULL,
            delete_preparation_token = NULL,
            delete_preparation_expires_at = NULL,
            delete_failure_terminal_status = ?,
            delete_failure_reason = ?,
            updated_at = NOW()
        WHERE id = ?
          AND current_delete_execution_intent_id = ?
        """,
        terminalStatus,
        failureReason,
        answerId,
        executionIntentId);
  }
}
