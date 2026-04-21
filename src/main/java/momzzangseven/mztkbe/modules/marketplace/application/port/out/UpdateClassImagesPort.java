package momzzangseven.mztkbe.modules.marketplace.application.port.out;

import java.util.List;

/**
 * Output port for delegating class image upsert to the image module.
 *
 * <p>Implemented by {@link
 * momzzangseven.mztkbe.modules.marketplace.infrastructure.external.image.ClassImageModuleAdapter},
 * which calls {@link
 * momzzangseven.mztkbe.modules.image.application.port.in.UpsertImagesByReferenceUseCase}.
 */
public interface UpdateClassImagesPort {

  /**
   * Update the full image set for a class. Existing bindings not in the new list are unlinked;
   * new/retained images are bound in order.
   *
   * <p>The first imageId must be of type {@code MARKET_CLASS_THUMB}; the remainder must be {@code
   * MARKET_CLASS_DETAIL}. Validation is performed inside {@code UpsertImagesByReferenceService}.
   *
   * @param trainerId owner ID for ownership validation
   * @param classId class whose images are being updated
   * @param imageIds ordered list of image IDs; empty list removes all images
   */
  void updateImages(Long trainerId, Long classId, List<Long> imageIds);
}
