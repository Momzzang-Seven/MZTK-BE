package momzzangseven.mztkbe.modules.image.application.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;

/**
 * Command for updating the image set of a community post (FREE or QUESTION type). Contains the
 * final ordered image IDs that should remain after the update.
 *
 * <p>imageIds: ordered list of image IDs to retain/add, where list index maps to img_order (0 → 1).
 */
public record UpdatePostImagesCommand(
    Long userId, Long postId, ImageReferenceType referenceType, List<Long> imageIds) {

  public void validate() {
    if (userId == null || userId <= 0)
      throw new IllegalArgumentException("userId must be positive");
    if (postId == null || postId <= 0)
      throw new IllegalArgumentException("postId must be positive");
    if (referenceType == null) throw new IllegalArgumentException("referenceType must not be null");
    if (imageIds == null) throw new IllegalArgumentException("imageIds must not be null");
  }
}
