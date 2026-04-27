package momzzangseven.mztkbe.modules.marketplace.classes.application.port.out;

import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassDetailResult.ImageInfo;

/**
 * Output port for querying class images from the image module.
 *
 * <p>Implemented by {@link
 * momzzangseven.mztkbe.modules.marketplace.infrastructure.external.image.ClassImageModuleAdapter}.
 */
public interface LoadClassImagesPort {

  /**
   * Load all images (THUMB + DETAIL) for a single class.
   *
   * @param classId class ID
   * @return result containing the thumbnail key and ordered detail image list
   */
  ClassImages loadImages(Long classId);

  /**
   * Batch-load thumbnail final-object-keys for multiple classes. Used in the listing page to avoid
   * N+1 queries.
   *
   * @param classIds list of class IDs
   * @return map of classId → thumbnail finalObjectKey (absent key means no thumbnail)
   */
  Map<Long, String> loadThumbnailKeys(List<Long> classIds);

  /**
   * Container holding the thumbnail key and ordered detail images for a single class.
   *
   * @param thumbnailFinalObjectKey S3 object key for the thumbnail, or null if not available
   * @param detailImages ordered list of detail image info (excludes the thumbnail)
   */
  record ClassImages(String thumbnailFinalObjectKey, List<ImageInfo> detailImages) {}
}
