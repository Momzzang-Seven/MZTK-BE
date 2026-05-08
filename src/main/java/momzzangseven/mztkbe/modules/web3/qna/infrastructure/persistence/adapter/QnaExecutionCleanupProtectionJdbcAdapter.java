package momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaExecutionCleanupProtectionQueryPort;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QnaExecutionCleanupProtectionJdbcAdapter
    implements QnaExecutionCleanupProtectionQueryPort {

  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  @Override
  public List<Long> filterDeletableFinalizedIntentIds(List<Long> candidateIntentIds) {
    if (candidateIntentIds == null || candidateIntentIds.isEmpty()) {
      return List.of();
    }
    return namedParameterJdbcTemplate.query(
        """
        SELECT e.id
        FROM web3_execution_intents e
        WHERE e.id IN (:ids)
          AND NOT (
              (
                  e.resource_type = 'QUESTION'
                  AND e.action_type = 'QNA_QUESTION_CREATE'
                  AND EXISTS (
                      SELECT 1
                      FROM posts p
                      WHERE CAST(p.id AS VARCHAR) = e.resource_id
                        AND p.user_id = e.requester_user_id
                        AND p.type = 'QUESTION'
                        AND p.publication_status = 'FAILED'
                  )
              )
              OR (
                  e.resource_type = 'ANSWER'
                  AND e.action_type IN ('QNA_ANSWER_SUBMIT', 'QNA_ANSWER_UPDATE', 'QNA_ANSWER_DELETE')
                  AND (
                      EXISTS (
                          SELECT 1
                          FROM answers a
                          WHERE a.current_create_execution_intent_id = e.public_id
                      )
                      OR EXISTS (
                          SELECT 1
                          FROM qna_answer_execution_intent_refs r
                          JOIN answers a
                            ON a.id = r.answer_id
                          WHERE r.execution_intent_public_id = e.public_id
                            AND r.action_type = 'QNA_ANSWER_SUBMIT'
                            AND a.publication_status = 'FAILED'
                      )
                      OR EXISTS (
                          SELECT 1
                          FROM answers a
                          WHERE a.current_delete_execution_intent_id = e.public_id
                      )
                      OR EXISTS (
                          SELECT 1
                          FROM qna_answer_update_states s
                          WHERE s.execution_intent_public_id = e.public_id
                            AND s.status IN ('PREPARING', 'INTENT_BOUND', 'PREPARATION_FAILED', 'FAILED', 'RECONCILIATION_REQUIRED')
                      )
                  )
              )
          )
        """,
        Map.of("ids", candidateIntentIds),
        (rs, rowNum) -> rs.getLong("id"));
  }
}
