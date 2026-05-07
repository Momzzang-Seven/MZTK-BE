package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.adapter;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateStatePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerUpdateStateJdbcAdapter implements AnswerUpdateStatePort {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public AnswerUpdateState createPreparing(
      Long answerId, String pendingContent, String preparationToken, LocalDateTime expiresAt) {
    Long updateVersion =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(update_version), 0) + 1 FROM qna_answer_update_states WHERE answer_id = ?",
            Long.class,
            answerId);
    String updateToken = UUID.randomUUID().toString();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          PreparedStatement statement =
              connection.prepareStatement(
                  """
                  INSERT INTO qna_answer_update_states (
                      answer_id,
                      update_version,
                      update_token,
                      preparation_token,
                      preparation_expires_at,
                      status,
                      pending_content,
                      created_at,
                      updated_at
                  )
                  VALUES (?, ?, ?, ?, ?, 'PREPARING', ?, NOW(), NOW())
                  """,
                  Statement.RETURN_GENERATED_KEYS);
          statement.setLong(1, answerId);
          statement.setLong(2, updateVersion);
          statement.setString(3, updateToken);
          statement.setString(4, preparationToken);
          statement.setObject(5, expiresAt);
          statement.setString(6, pendingContent);
          return statement;
        },
        keyHolder);
    Number generatedId = extractGeneratedId(keyHolder);
    if (generatedId == null) {
      throw new IllegalStateException("answer update state id was not generated");
    }
    return loadById(generatedId.longValue())
        .orElseThrow(() -> new IllegalStateException("created answer update state is missing"));
  }

  @Override
  public int bindIntentIfCurrent(
      Long answerId,
      Long updateVersion,
      String updateToken,
      String preparationToken,
      String executionIntentId) {
    return jdbcTemplate.update(
        """
        UPDATE qna_answer_update_states
        SET status = 'INTENT_BOUND',
            execution_intent_public_id = ?,
            preparation_token = NULL,
            preparation_expires_at = NULL,
            updated_at = NOW()
        WHERE answer_id = ?
          AND update_version = ?
          AND update_token = ?
          AND preparation_token = ?
          AND status = 'PREPARING'
          AND execution_intent_public_id IS NULL
        """,
        executionIntentId,
        answerId,
        updateVersion,
        updateToken,
        preparationToken);
  }

  @Override
  public Optional<AnswerUpdateState> loadIntentBoundState(
      Long answerId, Long updateVersion, String updateToken, String executionIntentId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, answer_id, update_version, update_token, execution_intent_public_id, pending_content, pending_image_update
            FROM qna_answer_update_states
            WHERE answer_id = ?
              AND update_version = ?
              AND update_token = ?
              AND execution_intent_public_id = ?
              AND status = 'INTENT_BOUND'
            """,
            (rs, rowNum) ->
                new AnswerUpdateState(
                    rs.getLong("id"),
                    rs.getLong("answer_id"),
                    rs.getLong("update_version"),
                    rs.getString("update_token"),
                    rs.getString("execution_intent_public_id"),
                    rs.getString("pending_content"),
                    rs.getBoolean("pending_image_update")),
            answerId,
            updateVersion,
            updateToken,
            executionIntentId)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<AnswerUpdateState> loadLatestRecoverable(Long answerId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, answer_id, update_version, update_token, execution_intent_public_id, pending_content, pending_image_update
            FROM qna_answer_update_states
            WHERE answer_id = ?
              AND status IN ('FAILED', 'PREPARATION_FAILED')
            ORDER BY update_version DESC
            LIMIT 1
            """,
            (rs, rowNum) ->
                new AnswerUpdateState(
                    rs.getLong("id"),
                    rs.getLong("answer_id"),
                    rs.getLong("update_version"),
                    rs.getString("update_token"),
                    rs.getString("execution_intent_public_id"),
                    rs.getString("pending_content"),
                    rs.getBoolean("pending_image_update")),
            answerId)
        .stream()
        .findFirst();
  }

  @Override
  public int markConfirmed(Long stateId) {
    return jdbcTemplate.update(
        "UPDATE qna_answer_update_states SET status = 'CONFIRMED', updated_at = NOW() WHERE id = ?",
        stateId);
  }

  @Override
  public int markFailedIfCurrent(
      Long answerId,
      Long updateVersion,
      String updateToken,
      String executionIntentId,
      String errorReason) {
    return jdbcTemplate.update(
        """
        UPDATE qna_answer_update_states
        SET status = 'FAILED',
            error_reason = ?,
            updated_at = NOW()
        WHERE answer_id = ?
          AND update_version = ?
          AND update_token = ?
          AND execution_intent_public_id = ?
          AND status = 'INTENT_BOUND'
        """,
        errorReason,
        answerId,
        updateVersion,
        updateToken,
        executionIntentId);
  }

  @Override
  public int markPreparationFailedIfCurrent(
      Long answerId,
      Long updateVersion,
      String updateToken,
      String preparationToken,
      String errorReason) {
    return jdbcTemplate.update(
        """
        UPDATE qna_answer_update_states
        SET status = 'PREPARATION_FAILED',
            error_reason = ?,
            updated_at = NOW()
        WHERE answer_id = ?
          AND update_version = ?
          AND update_token = ?
          AND preparation_token = ?
          AND execution_intent_public_id IS NULL
          AND status = 'PREPARING'
        """,
        errorReason,
        answerId,
        updateVersion,
        updateToken,
        preparationToken);
  }

  @Override
  public int discardLatestFailed(Long answerId) {
    return jdbcTemplate.update(
        """
        UPDATE qna_answer_update_states
        SET status = 'DISCARDED',
            updated_at = NOW()
        WHERE id = (
            SELECT id
            FROM qna_answer_update_states
            WHERE answer_id = ?
              AND status IN ('FAILED', 'PREPARATION_FAILED')
            ORDER BY update_version DESC
            LIMIT 1
        )
        """,
        answerId);
  }

  @Override
  public int bindRecoveryIntentIfCurrent(Long stateId, String executionIntentId) {
    return jdbcTemplate.update(
        """
        UPDATE qna_answer_update_states
        SET status = 'INTENT_BOUND',
            execution_intent_public_id = ?,
            error_code = NULL,
            error_reason = NULL,
            updated_at = NOW()
        WHERE id = ?
          AND status IN ('FAILED', 'PREPARATION_FAILED')
        """,
        executionIntentId,
        stateId);
  }

  @Override
  public boolean hasBlockingUpdate(Long answerId) {
    Boolean exists =
        jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM qna_answer_update_states
                WHERE answer_id = ?
                  AND status IN ('PREPARING', 'INTENT_BOUND', 'PREPARATION_FAILED', 'FAILED', 'RECONCILIATION_REQUIRED')
            )
            """,
            Boolean.class,
            answerId);
    return Boolean.TRUE.equals(exists);
  }

  private Optional<AnswerUpdateState> loadById(Long stateId) {
    return jdbcTemplate
        .query(
            """
            SELECT id, answer_id, update_version, update_token, execution_intent_public_id, pending_content, pending_image_update
            FROM qna_answer_update_states
            WHERE id = ?
            """,
            (rs, rowNum) ->
                new AnswerUpdateState(
                    rs.getLong("id"),
                    rs.getLong("answer_id"),
                    rs.getLong("update_version"),
                    rs.getString("update_token"),
                    rs.getString("execution_intent_public_id"),
                    rs.getString("pending_content"),
                    rs.getBoolean("pending_image_update")),
            stateId)
        .stream()
        .findFirst();
  }

  private Number extractGeneratedId(KeyHolder keyHolder) {
    if (keyHolder.getKeys() != null && keyHolder.getKeys().get("ID") instanceof Number id) {
      return id;
    }
    return keyHolder.getKey();
  }
}
