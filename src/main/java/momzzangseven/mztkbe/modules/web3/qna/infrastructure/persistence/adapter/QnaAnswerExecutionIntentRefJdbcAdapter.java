package momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.ManageQnaAnswerExecutionIntentRefPort;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaExecutionActionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QnaAnswerExecutionIntentRefJdbcAdapter
    implements ManageQnaAnswerExecutionIntentRefPort {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void upsert(QnaAnswerExecutionIntentRef ref) {
    jdbcTemplate.update(
        """
        INSERT INTO qna_answer_execution_intent_refs (
            execution_intent_public_id,
            post_id,
            answer_id,
            action_type,
            status_snapshot,
            created_at,
            updated_at
        )
        VALUES (?, ?, ?, ?, ?, NOW(), NOW())
        ON CONFLICT (execution_intent_public_id) DO UPDATE
        SET post_id = EXCLUDED.post_id,
            answer_id = EXCLUDED.answer_id,
            action_type = EXCLUDED.action_type,
            status_snapshot = EXCLUDED.status_snapshot,
            updated_at = NOW()
        """,
        ref.executionIntentId(),
        ref.postId(),
        ref.answerId(),
        ref.actionType().name(),
        ref.statusSnapshot());
  }

  @Override
  public Optional<QnaAnswerExecutionIntentRef> findByExecutionIntentId(String executionIntentId) {
    return jdbcTemplate
        .query(
            """
            SELECT execution_intent_public_id, post_id, answer_id, action_type, status_snapshot
            FROM qna_answer_execution_intent_refs
            WHERE execution_intent_public_id = ?
            """,
            (rs, rowNum) ->
                new QnaAnswerExecutionIntentRef(
                    rs.getString("execution_intent_public_id"),
                    rs.getLong("post_id"),
                    rs.getLong("answer_id"),
                    QnaExecutionActionType.valueOf(rs.getString("action_type")),
                    rs.getString("status_snapshot")),
            executionIntentId)
        .stream()
        .findFirst();
  }

  @Override
  public List<QnaAnswerExecutionIntentRef> findByPostIdAndActionType(
      Long postId, QnaExecutionActionType actionType) {
    return jdbcTemplate.query(
        """
        SELECT execution_intent_public_id, post_id, answer_id, action_type, status_snapshot
        FROM qna_answer_execution_intent_refs
        WHERE post_id = ?
          AND action_type = ?
        ORDER BY id DESC
        """,
        (rs, rowNum) ->
            new QnaAnswerExecutionIntentRef(
                rs.getString("execution_intent_public_id"),
                rs.getLong("post_id"),
                rs.getLong("answer_id"),
                QnaExecutionActionType.valueOf(rs.getString("action_type")),
                rs.getString("status_snapshot")),
        postId,
        actionType.name());
  }
}
