package momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.adapter;

import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.ManageQnaAnswerExecutionIntentRefPort.QnaAnswerExecutionIntentRef;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaExecutionCleanupProtectionQueryPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QnaExecutionCleanupProtectionJdbcAdapter
    implements QnaExecutionCleanupProtectionQueryPort {

  private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

  @Override
  public Optional<QnaAnswerExecutionIntentRef> findAnswerExecutionIntentRef(
      String executionIntentId) {
    return namedParameterJdbcTemplate
        .query(
            """
            SELECT execution_intent_public_id, post_id, answer_id, action_type, status_snapshot
            FROM qna_answer_execution_intent_refs
            WHERE execution_intent_public_id = :executionIntentId
            """,
            Map.of("executionIntentId", executionIntentId),
            (rs, rowNum) ->
                new QnaAnswerExecutionIntentRef(
                    rs.getString("execution_intent_public_id"),
                    rs.getLong("post_id"),
                    rs.getLong("answer_id"),
                    QnaExecutionActionType.valueOf(rs.getString("action_type")),
                    rs.getString("status_snapshot")))
        .stream()
        .findFirst();
  }

  @Override
  public boolean hasProtectedAnswerUpdateState(String executionIntentId) {
    Boolean result =
        namedParameterJdbcTemplate.queryForObject(
            """
        SELECT EXISTS (
            SELECT 1
            FROM qna_answer_update_states s
            WHERE s.execution_intent_public_id = :executionIntentId
              AND s.status IN (
                  'PREPARING',
                  'INTENT_BOUND',
                  'PREPARATION_FAILED',
                  'FAILED',
                  'RECONCILIATION_REQUIRED'
              )
        )
        """,
            Map.of("executionIntentId", executionIntentId),
            Boolean.class);
    return Boolean.TRUE.equals(result);
  }
}
