package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.adapter;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateImageStatePort;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerUpdateImageStateJdbcAdapter implements AnswerUpdateImageStatePort {

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void markPendingImageUpdate(Long updateStateId) {
    jdbcTemplate.update(
        """
        UPDATE qna_answer_update_states
        SET pending_image_update = true,
            updated_at = NOW()
        WHERE id = ?
        """,
        updateStateId);
  }

  @Override
  public void replacePendingImages(Long updateStateId, List<Long> imageIds) {
    jdbcTemplate.update(
        "DELETE FROM qna_answer_update_images WHERE update_state_id = ?", updateStateId);
    if (imageIds.isEmpty()) {
      return;
    }
    jdbcTemplate.batchUpdate(
        """
        INSERT INTO qna_answer_update_images (
            update_state_id,
            image_id,
            image_order,
            created_at,
            updated_at
        )
        VALUES (?, ?, ?, NOW(), NOW())
        """,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            ps.setLong(1, updateStateId);
            ps.setLong(2, imageIds.get(i));
            ps.setInt(3, i + 1);
          }

          @Override
          public int getBatchSize() {
            return imageIds.size();
          }
        });
  }

  @Override
  public boolean hasPendingImageUpdate(Long updateStateId) {
    Boolean result =
        jdbcTemplate.queryForObject(
            "SELECT pending_image_update FROM qna_answer_update_states WHERE id = ?",
            Boolean.class,
            updateStateId);
    return Boolean.TRUE.equals(result);
  }

  @Override
  public List<Long> loadPendingImageIds(Long updateStateId) {
    return jdbcTemplate.query(
        """
        SELECT image_id
        FROM qna_answer_update_images
        WHERE update_state_id = ?
        ORDER BY image_order ASC
        """,
        (rs, rowNum) -> rs.getLong("image_id"),
        updateStateId);
  }

  @Override
  public List<Long> findDiscardedUpdateStateIds(Long answerId) {
    return jdbcTemplate.query(
        """
        SELECT id
        FROM qna_answer_update_states
        WHERE answer_id = ?
          AND status = 'DISCARDED'
        ORDER BY id ASC
        """,
        (rs, rowNum) -> rs.getLong("id"),
        answerId);
  }
}
