package momzzangseven.mztkbe.modules.image.application.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;

/**
 * Command for upserting the image set of a given reference. Contains the final ordered image IDs
 * that should remain after the upsert.
 *
 * <p>imageIds: ordered list of image IDs to retain/add, where list index maps to img_order (0 → 1).
 */
public record UpsertImagesByReferenceCommand(
    Long userId, Long referenceId, ImageReferenceType referenceType, List<Long> imageIds) {

  public void validate() {
    if (userId == null || userId <= 0)
      throw new IllegalArgumentException("userId must be positive");
    if (referenceId == null || referenceId <= 0)
      throw new IllegalArgumentException("referenceId must be positive");
    if (referenceType == null) throw new IllegalArgumentException("referenceType must not be null");
    if (imageIds == null) throw new IllegalArgumentException("imageIds must not be null");
    for (int i = 0; i < imageIds.size(); i++) {
      Long id = imageIds.get(i);
      if (id == null || id <= 0) throw new IllegalArgumentException("imageIds must be positive");
    }
  }
}
