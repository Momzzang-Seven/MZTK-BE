package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.adapter;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.image.ImageMaxCountExceedException;
import momzzangseven.mztkbe.global.error.image.ImageNotBelongsToUserException;
import momzzangseven.mztkbe.global.error.image.ImageNotFoundException;
import momzzangseven.mztkbe.global.error.image.InvalidImageRefTypeException;
import momzzangseven.mztkbe.modules.answer.application.port.out.AnswerUpdateImagePort;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageCountPolicy;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerUpdateImageJdbcAdapter implements AnswerUpdateImagePort {

  private static final String COMMUNITY_ANSWER = ImageReferenceType.COMMUNITY_ANSWER.name();
  private static final String COMMUNITY_ANSWER_UPDATE =
      ImageReferenceType.COMMUNITY_ANSWER_UPDATE.name();

  private final JdbcTemplate jdbcTemplate;

  @Override
  public void savePendingImages(
      Long updateStateId, Long userId, Long answerId, List<Long> imageIds) {
    validateRequired(updateStateId, "updateStateId");
    validateRequired(userId, "userId");
    validateRequired(answerId, "answerId");
    if (imageIds == null) {
      throw new IllegalArgumentException("imageIds must not be null");
    }
    validateImageIds(imageIds);

    jdbcTemplate.update(
        """
        UPDATE qna_answer_update_states
        SET pending_image_update = true,
            updated_at = NOW()
        WHERE id = ?
        """,
        updateStateId);

    jdbcTemplate.update(
        "DELETE FROM qna_answer_update_images WHERE update_state_id = ?", updateStateId);
    if (imageIds.isEmpty()) {
      return;
    }

    Map<Long, ImageRow> imagesById = loadImagesForUpdate(imageIds);
    validateImages(imagesById, imageIds, userId, answerId, updateStateId);
    reserveNewImages(updateStateId, imageIds, imagesById);
    insertPendingImages(updateStateId, imageIds);
  }

  @Override
  public void applyPendingImages(Long updateStateId, Long userId, Long answerId) {
    validateRequired(updateStateId, "updateStateId");
    validateRequired(userId, "userId");
    validateRequired(answerId, "answerId");
    if (!hasPendingImageUpdate(updateStateId)) {
      return;
    }

    List<Long> imageIds = loadPendingImageIds(updateStateId);
    unlinkRemovedAnswerImages(updateStateId, answerId);
    int linkedCount = linkPendingImages(updateStateId, answerId);
    if (linkedCount != imageIds.size()) {
      throw new InvalidImageRefTypeException(
          "Pending answer update images are no longer attachable");
    }
  }

  @Override
  public void releasePendingImages(Long answerId) {
    validateRequired(answerId, "answerId");
    jdbcTemplate.update(
        """
        UPDATE images
        SET reference_type = 'COMMUNITY_ANSWER',
            reference_id = NULL,
            img_order = NULL,
            updated_at = NOW()
        WHERE reference_type = 'COMMUNITY_ANSWER_UPDATE'
          AND reference_id IN (
              SELECT id
              FROM qna_answer_update_states
              WHERE answer_id = ?
                AND status = 'DISCARDED'
          )
        """,
        answerId);
  }

  private void validateRequired(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException(fieldName + " must be positive");
    }
  }

  private void validateImageIds(List<Long> imageIds) {
    int maxCount = ImageCountPolicy.of(ImageReferenceType.COMMUNITY_ANSWER).getMaxCount();
    if (imageIds.size() > maxCount) {
      throw new ImageMaxCountExceedException(
          "Image count "
              + imageIds.size()
              + " exceeds the limit of "
              + maxCount
              + " for type "
              + COMMUNITY_ANSWER);
    }
    Set<Long> uniqueIds = new LinkedHashSet<>();
    for (Long imageId : imageIds) {
      if (imageId == null || imageId <= 0) {
        throw new IllegalArgumentException("imageIds must be positive");
      }
      if (!uniqueIds.add(imageId)) {
        throw new InvalidImageRefTypeException("Duplicate image id is not allowed");
      }
    }
  }

  private Map<Long, ImageRow> loadImagesForUpdate(List<Long> imageIds) {
    if (imageIds.isEmpty()) {
      return Map.of();
    }
    String placeholders = String.join(",", imageIds.stream().map(id -> "?").toList());
    return jdbcTemplate
        .query(
            "SELECT id, user_id, reference_type, reference_id FROM images WHERE id IN ("
                + placeholders
                + ") FOR UPDATE",
            (rs, rowNum) ->
                new ImageRow(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getString("reference_type"),
                    rs.getObject("reference_id", Long.class)),
            imageIds.toArray())
        .stream()
        .collect(LinkedHashMap::new, (map, row) -> map.put(row.id(), row), LinkedHashMap::putAll);
  }

  private void validateImages(
      Map<Long, ImageRow> imagesById,
      List<Long> imageIds,
      Long userId,
      Long answerId,
      Long updateStateId) {
    for (Long imageId : imageIds) {
      ImageRow image = imagesById.get(imageId);
      if (image == null) {
        throw new ImageNotFoundException("Requested image not found: id=" + imageId);
      }
      if (!userId.equals(image.userId())) {
        throw new ImageNotBelongsToUserException("Image does not belong to user");
      }
      if (COMMUNITY_ANSWER.equals(image.referenceType())) {
        if (image.referenceId() != null && !answerId.equals(image.referenceId())) {
          throw new InvalidImageRefTypeException("Image is already linked to a different entity");
        }
        continue;
      }
      if (COMMUNITY_ANSWER_UPDATE.equals(image.referenceType())
          && updateStateId.equals(image.referenceId())) {
        continue;
      }
      throw new InvalidImageRefTypeException("New image has different reference type with command");
    }
  }

  private void reserveNewImages(
      Long updateStateId, List<Long> imageIds, Map<Long, ImageRow> imagesById) {
    List<Long> reservableImageIds = new ArrayList<>();
    for (Long imageId : imageIds) {
      ImageRow image = imagesById.get(imageId);
      if (COMMUNITY_ANSWER.equals(image.referenceType()) && image.referenceId() == null) {
        reservableImageIds.add(imageId);
      }
    }
    if (reservableImageIds.isEmpty()) {
      return;
    }
    String placeholders = String.join(",", reservableImageIds.stream().map(id -> "?").toList());
    List<Object> params = new ArrayList<>();
    params.add(updateStateId);
    params.addAll(reservableImageIds);
    jdbcTemplate.update(
        "UPDATE images SET reference_type = 'COMMUNITY_ANSWER_UPDATE', reference_id = ?, "
            + "img_order = NULL, updated_at = NOW() WHERE id IN ("
            + placeholders
            + ")",
        params.toArray());
  }

  private void insertPendingImages(Long updateStateId, List<Long> imageIds) {
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

  private boolean hasPendingImageUpdate(Long updateStateId) {
    Boolean result =
        jdbcTemplate.queryForObject(
            "SELECT pending_image_update FROM qna_answer_update_states WHERE id = ?",
            Boolean.class,
            updateStateId);
    return Boolean.TRUE.equals(result);
  }

  private List<Long> loadPendingImageIds(Long updateStateId) {
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

  private void unlinkRemovedAnswerImages(Long updateStateId, Long answerId) {
    jdbcTemplate.update(
        """
        UPDATE images i
        SET reference_id = NULL,
            img_order = NULL,
            updated_at = NOW()
        WHERE i.reference_type = 'COMMUNITY_ANSWER'
          AND i.reference_id = ?
          AND NOT EXISTS (
              SELECT 1
              FROM qna_answer_update_images pi
              WHERE pi.update_state_id = ?
                AND pi.image_id = i.id
          )
        """,
        answerId,
        updateStateId);
  }

  private int linkPendingImages(Long updateStateId, Long answerId) {
    return jdbcTemplate.update(
        """
        UPDATE images i
        SET reference_type = 'COMMUNITY_ANSWER',
            reference_id = ?,
            img_order = pi.image_order,
            updated_at = NOW()
        FROM qna_answer_update_images pi
        WHERE pi.update_state_id = ?
          AND pi.image_id = i.id
          AND (
              (i.reference_type = 'COMMUNITY_ANSWER' AND i.reference_id = ?)
              OR (i.reference_type = 'COMMUNITY_ANSWER_UPDATE' AND i.reference_id = ?)
          )
        """,
        answerId,
        updateStateId,
        answerId,
        updateStateId);
  }

  private record ImageRow(Long id, Long userId, String referenceType, Long referenceId) {}
}
