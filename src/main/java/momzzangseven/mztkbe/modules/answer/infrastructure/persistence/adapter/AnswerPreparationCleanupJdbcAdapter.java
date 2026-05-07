package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.adapter;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPreparationCleanupPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerPreparationCleanupJdbcAdapter implements AnswerPreparationCleanupPort {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public List<Long> findExpiredCreatePreparationAnswerIds(LocalDateTime now, int batchSize) {
    return jdbcTemplate.query(
        """
        SELECT id
        FROM answers
        WHERE create_preparation_token IS NOT NULL
          AND create_preparation_expires_at <= ?
          AND current_create_execution_intent_id IS NULL
        ORDER BY create_preparation_expires_at ASC, id ASC
        LIMIT ?
        """,
        (rs, rowNum) -> rs.getLong("id"),
        Timestamp.valueOf(now),
        batchSize);
  }

  @Override
  public List<Long> deleteCreatePreparationAnswers(List<Long> answerIds) {
    if (answerIds == null || answerIds.isEmpty()) {
      return List.of();
    }
    String placeholders = String.join(",", answerIds.stream().map(id -> "?").toList());
    Object[] params = answerIds.toArray();
    return jdbcTemplate.query(
        "DELETE FROM answers "
            + "WHERE create_preparation_token IS NOT NULL "
            + "AND current_create_execution_intent_id IS NULL "
            + "AND id IN ("
            + placeholders
            + ") RETURNING id",
        (rs, rowNum) -> rs.getLong("id"),
        params);
  }

  @Override
  public int expireDeletePreparations(LocalDateTime now, int batchSize) {
    return jdbcTemplate.update(
        """
        UPDATE answers
        SET pending_delete_status = NULL,
            delete_preparation_token = NULL,
            delete_preparation_expires_at = NULL,
            delete_failure_terminal_status = 'EXPIRED',
            delete_failure_reason = 'delete preparation expired',
            updated_at = NOW()
        WHERE id IN (
            SELECT id
            FROM answers
            WHERE pending_delete_status = 'PREPARING'
              AND current_delete_execution_intent_id IS NULL
              AND delete_preparation_expires_at <= ?
            ORDER BY delete_preparation_expires_at ASC, id ASC
            LIMIT ?
        )
        """,
        Timestamp.valueOf(now),
        batchSize);
  }

  @Override
  public int expireUpdatePreparations(LocalDateTime now, int batchSize) {
    return jdbcTemplate.update(
        """
        UPDATE qna_answer_update_states
        SET status = 'PREPARATION_FAILED',
            retryable = true,
            error_reason = 'answer update preparation expired',
            preparation_token = NULL,
            preparation_expires_at = NULL,
            updated_at = NOW()
        WHERE id IN (
            SELECT id
            FROM qna_answer_update_states
            WHERE status = 'PREPARING'
              AND execution_intent_public_id IS NULL
              AND preparation_expires_at <= ?
            ORDER BY preparation_expires_at ASC, id ASC
            LIMIT ?
        )
        """,
        Timestamp.valueOf(now),
        batchSize);
  }
}
