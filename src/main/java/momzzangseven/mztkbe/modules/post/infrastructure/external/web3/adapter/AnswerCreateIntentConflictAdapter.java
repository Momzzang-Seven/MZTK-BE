package momzzangseven.mztkbe.modules.post.infrastructure.external.web3.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadAnswerCreateIntentConflictPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerCreateIntentConflictAdapter implements LoadAnswerCreateIntentConflictPort {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public boolean hasActiveAnswerCreateIntent(Long postId) {
    if (postId == null) {
      return false;
    }
    Boolean exists =
        jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM qna_answer_execution_intent_refs r
                JOIN web3_execution_intents e
                  ON e.public_id = r.execution_intent_public_id
                WHERE r.post_id = ?
                  AND r.action_type = 'QNA_ANSWER_SUBMIT'
                  AND e.status IN ('AWAITING_SIGNATURE', 'SIGNED', 'PENDING_ONCHAIN')
            )
            """,
            Boolean.class,
            postId);
    return Boolean.TRUE.equals(exists);
  }
}
