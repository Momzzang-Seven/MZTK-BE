package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.adapter;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerPublicationReconciliationPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerPublicationReconciliationJdbcAdapter
    implements AnswerPublicationReconciliationPort {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public int reconcileConfirmedSubmits(int batchSize) {
    return jdbcTemplate.update(
        """
        UPDATE answers a
        SET publication_status = 'VISIBLE',
            current_create_execution_intent_id = NULL,
            create_preparation_token = NULL,
            create_preparation_expires_at = NULL,
            publication_failure_terminal_status = NULL,
            publication_failure_reason = NULL,
            updated_at = NOW()
        WHERE a.id IN (
            SELECT a2.id
            FROM answers a2
            JOIN web3_execution_intents e
              ON e.public_id = a2.current_create_execution_intent_id
            JOIN web3_qna_answers qa
              ON qa.answer_id = a2.id
            WHERE e.resource_type = 'ANSWER'
              AND e.action_type = 'QNA_ANSWER_SUBMIT'
              AND e.status = 'CONFIRMED'
              AND a2.publication_status <> 'VISIBLE'
            ORDER BY a2.id
            LIMIT ?
        )
        """,
        batchSize);
  }

  @Override
  public int reconcileTerminalSubmitFailures(int batchSize) {
    return jdbcTemplate.update(
        """
        UPDATE answers a
        SET publication_status = 'FAILED',
            current_create_execution_intent_id = NULL,
            create_preparation_token = NULL,
            create_preparation_expires_at = NULL,
            publication_failure_terminal_status = e.status,
            publication_failure_reason = COALESCE(e.last_error_reason, 'answer submit terminal reconciliation'),
            updated_at = NOW()
        FROM web3_execution_intents e
        WHERE a.id IN (
            SELECT a2.id
            FROM answers a2
            JOIN web3_execution_intents e2
              ON e2.public_id = a2.current_create_execution_intent_id
            LEFT JOIN web3_qna_answers qa
              ON qa.answer_id = a2.id
            WHERE e2.resource_type = 'ANSWER'
              AND e2.action_type = 'QNA_ANSWER_SUBMIT'
              AND e2.status IN ('FAILED_ONCHAIN', 'EXPIRED', 'CANCELED', 'NONCE_STALE')
              AND qa.answer_id IS NULL
            ORDER BY a2.id
            LIMIT ?
        )
          AND e.public_id = a.current_create_execution_intent_id
        """,
        batchSize);
  }

  @Override
  public int reconcileConfirmedUpdates(int batchSize) {
    int answersUpdated =
        jdbcTemplate.update(
            """
            UPDATE answers a
            SET content = s.pending_content,
                updated_at = NOW()
            FROM qna_answer_update_states s
            JOIN web3_execution_intents e
              ON e.public_id = s.execution_intent_public_id
            WHERE a.id = s.answer_id
              AND s.status = 'INTENT_BOUND'
              AND e.resource_type = 'ANSWER'
              AND e.action_type = 'QNA_ANSWER_UPDATE'
              AND e.status = 'CONFIRMED'
              AND s.id IN (
                  SELECT s2.id
                  FROM qna_answer_update_states s2
                  JOIN web3_execution_intents e2
                    ON e2.public_id = s2.execution_intent_public_id
                  WHERE s2.status = 'INTENT_BOUND'
                    AND e2.resource_type = 'ANSWER'
                    AND e2.action_type = 'QNA_ANSWER_UPDATE'
                    AND e2.status = 'CONFIRMED'
                  ORDER BY s2.answer_id, s2.update_version
                  LIMIT ?
              )
            """,
            batchSize);
    applyConfirmedUpdateImages(batchSize);
    jdbcTemplate.update(
        """
        UPDATE qna_answer_update_states s
        SET status = 'RECONCILIATION_REQUIRED',
            reconciliation_required_reason = 'confirmed answer update image reconciliation failed',
            updated_at = NOW()
        FROM answers a,
             web3_execution_intents e
        WHERE a.id = s.answer_id
          AND e.public_id = s.execution_intent_public_id
          AND a.content = s.pending_content
          AND s.status = 'INTENT_BOUND'
          AND s.pending_image_update = true
          AND e.resource_type = 'ANSWER'
          AND e.action_type = 'QNA_ANSWER_UPDATE'
          AND e.status = 'CONFIRMED'
          AND NOT (
              NOT EXISTS (
                  SELECT 1
                  FROM images i
                  WHERE i.reference_type = 'COMMUNITY_ANSWER'
                    AND i.reference_id = s.answer_id
                    AND NOT EXISTS (
                        SELECT 1
                        FROM qna_answer_update_images pi
                        WHERE pi.update_state_id = s.id
                          AND pi.image_id = i.id
                          AND pi.image_order = i.img_order
                    )
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM qna_answer_update_images pi
                  LEFT JOIN images i
                    ON i.id = pi.image_id
                   AND i.reference_type = 'COMMUNITY_ANSWER'
                   AND i.reference_id = s.answer_id
                   AND i.img_order = pi.image_order
                  WHERE pi.update_state_id = s.id
                    AND i.id IS NULL
              )
          )
        """);
    jdbcTemplate.update(
        """
        UPDATE qna_answer_update_states s
        SET status = 'CONFIRMED',
            updated_at = NOW()
        FROM answers a,
             web3_execution_intents e
        WHERE a.id = s.answer_id
          AND e.public_id = s.execution_intent_public_id
          AND a.content = s.pending_content
          AND s.status = 'INTENT_BOUND'
          AND e.resource_type = 'ANSWER'
          AND e.action_type = 'QNA_ANSWER_UPDATE'
          AND e.status = 'CONFIRMED'
          AND (
              s.pending_image_update = false
              OR (
                  NOT EXISTS (
                      SELECT 1
                      FROM images i
                      WHERE i.reference_type = 'COMMUNITY_ANSWER'
                        AND i.reference_id = s.answer_id
                        AND NOT EXISTS (
                            SELECT 1
                            FROM qna_answer_update_images pi
                            WHERE pi.update_state_id = s.id
                              AND pi.image_id = i.id
                              AND pi.image_order = i.img_order
                        )
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM qna_answer_update_images pi
                      LEFT JOIN images i
                        ON i.id = pi.image_id
                       AND i.reference_type = 'COMMUNITY_ANSWER'
                       AND i.reference_id = s.answer_id
                       AND i.img_order = pi.image_order
                      WHERE pi.update_state_id = s.id
                        AND i.id IS NULL
                  )
              )
          )
        """);
    return answersUpdated;
  }

  private void applyConfirmedUpdateImages(int batchSize) {
    jdbcTemplate.update(
        """
        WITH target_states AS (
            SELECT s2.id, s2.answer_id, s2.pending_image_update
            FROM qna_answer_update_states s2
            JOIN web3_execution_intents e2
              ON e2.public_id = s2.execution_intent_public_id
            WHERE s2.status = 'INTENT_BOUND'
              AND s2.pending_image_update = true
              AND e2.resource_type = 'ANSWER'
              AND e2.action_type = 'QNA_ANSWER_UPDATE'
              AND e2.status = 'CONFIRMED'
              AND NOT EXISTS (
                  SELECT 1
                  FROM qna_answer_update_images pi
                  LEFT JOIN images i
                    ON i.id = pi.image_id
                  WHERE pi.update_state_id = s2.id
                    AND (
                        i.id IS NULL
                        OR NOT (
                            (i.reference_type = 'COMMUNITY_ANSWER' AND i.reference_id = s2.answer_id)
                            OR (i.reference_type = 'COMMUNITY_ANSWER_UPDATE' AND i.reference_id = s2.id)
                        )
                    )
              )
            ORDER BY s2.answer_id, s2.update_version
            LIMIT ?
        )
        UPDATE images i
        SET reference_id = NULL,
            img_order = NULL,
            updated_at = NOW()
        FROM target_states s
        WHERE i.reference_type = 'COMMUNITY_ANSWER'
          AND i.reference_id = s.answer_id
          AND NOT EXISTS (
              SELECT 1
              FROM qna_answer_update_images pi
              WHERE pi.update_state_id = s.id
                AND pi.image_id = i.id
          )
        """,
        batchSize);
    jdbcTemplate.update(
        """
        WITH target_states AS (
            SELECT s2.id, s2.answer_id, s2.pending_image_update
            FROM qna_answer_update_states s2
            JOIN web3_execution_intents e2
              ON e2.public_id = s2.execution_intent_public_id
            WHERE s2.status = 'INTENT_BOUND'
              AND s2.pending_image_update = true
              AND e2.resource_type = 'ANSWER'
              AND e2.action_type = 'QNA_ANSWER_UPDATE'
              AND e2.status = 'CONFIRMED'
              AND NOT EXISTS (
                  SELECT 1
                  FROM qna_answer_update_images pi
                  LEFT JOIN images i
                    ON i.id = pi.image_id
                  WHERE pi.update_state_id = s2.id
                    AND (
                        i.id IS NULL
                        OR NOT (
                            (i.reference_type = 'COMMUNITY_ANSWER' AND i.reference_id = s2.answer_id)
                            OR (i.reference_type = 'COMMUNITY_ANSWER_UPDATE' AND i.reference_id = s2.id)
                        )
                    )
              )
            ORDER BY s2.answer_id, s2.update_version
            LIMIT ?
        )
        UPDATE images i
        SET reference_type = 'COMMUNITY_ANSWER',
            reference_id = s.answer_id,
            img_order = pi.image_order,
            updated_at = NOW()
        FROM target_states s
        JOIN qna_answer_update_images pi
          ON pi.update_state_id = s.id
        WHERE pi.image_id = i.id
          AND (
              (i.reference_type = 'COMMUNITY_ANSWER' AND i.reference_id = s.answer_id)
              OR (i.reference_type = 'COMMUNITY_ANSWER_UPDATE' AND i.reference_id = s.id)
          )
        """,
        batchSize);
  }

  @Override
  public List<Long> findConfirmedDeleteAnswerIds(int batchSize) {
    return jdbcTemplate.query(
        """
        SELECT a.id
        FROM answers a
        JOIN web3_execution_intents e
          ON e.public_id = a.current_delete_execution_intent_id
        WHERE e.resource_type = 'ANSWER'
          AND e.action_type = 'QNA_ANSWER_DELETE'
          AND e.status = 'CONFIRMED'
        ORDER BY a.id
        LIMIT ?
        """,
        (rs, rowNum) -> rs.getLong("id"),
        batchSize);
  }

  @Override
  public int deleteConfirmedDeleteAnswers(List<Long> answerIds) {
    if (answerIds == null || answerIds.isEmpty()) {
      return 0;
    }
    String placeholders = String.join(",", answerIds.stream().map(id -> "?").toList());
    return jdbcTemplate.update(
        "DELETE FROM answers WHERE id IN (" + placeholders + ")", answerIds.toArray());
  }

  @Override
  public int repairQuestionAnswerCounts() {
    return jdbcTemplate.update(
        """
        UPDATE web3_qna_questions q
        SET answer_count = counts.answer_count,
            updated_at = NOW()
        FROM (
            SELECT q2.post_id, COUNT(a.answer_id)::INTEGER AS answer_count
            FROM web3_qna_questions q2
            LEFT JOIN web3_qna_answers a
              ON a.post_id = q2.post_id
            GROUP BY q2.post_id
        ) counts
        WHERE q.post_id = counts.post_id
          AND q.answer_count <> counts.answer_count
        """);
  }
}
