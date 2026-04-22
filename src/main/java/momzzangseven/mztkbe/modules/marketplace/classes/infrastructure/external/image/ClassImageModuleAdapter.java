package momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.external.image;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult.ImageItem;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesResult;
import momzzangseven.mztkbe.modules.image.application.dto.UpsertImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByReferencesUseCase;
import momzzangseven.mztkbe.modules.image.application.port.in.UpsertImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.GetClassDetailResult.ImageInfo;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassImagesPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.UpdateClassImagesPort;
import org.springframework.stereotype.Component;

/**
 * Adapter bridging the marketplace class module with the image module.
 *
 * <p>Implements both {@link UpdateClassImagesPort} and {@link LoadClassImagesPort}. Follows the
 * exact same pattern as {@code post.infrastructure.external.image.ImageModuleAdapter}.
 *
 * <ul>
 *   <li>{@code updateImages} delegates to {@link UpsertImagesByReferenceUseCase} using {@link
 *       ImageReferenceType#MARKET_CLASS} (the virtual aggregate type).
 *   <li>{@code loadImages} delegates to {@link GetImagesByReferenceUseCase} to get both THUMB and
 *       DETAIL images.
 *   <li>{@code loadThumbnailKeys} delegates to {@link GetImagesByReferencesUseCase} for batch
 *       thumbnail loading (N+1 prevention).
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ClassImageModuleAdapter implements UpdateClassImagesPort, LoadClassImagesPort {

  private final UpsertImagesByReferenceUseCase upsertImagesByReferenceUseCase;
  private final GetImagesByReferenceUseCase getImagesByReferenceUseCase;
  private final GetImagesByReferencesUseCase getImagesByReferencesUseCase;

  // ========== UpdateClassImagesPort ==========

  @Override
  public void updateImages(Long trainerId, Long classId, List<Long> imageIds) {
    upsertImagesByReferenceUseCase.execute(
        new UpsertImagesByReferenceCommand(
            trainerId, classId, ImageReferenceType.MARKET_CLASS, imageIds));
  }

  // ========== LoadClassImagesPort ==========

  @Override
  public ClassImages loadImages(Long classId) {
    GetImagesByReferenceResult result =
        getImagesByReferenceUseCase.execute(
            new GetImagesByReferenceCommand(ImageReferenceType.MARKET_CLASS, classId));

    String thumbnailKey = null;
    List<ImageInfo> detailImages = new ArrayList<>();
    int detailOrder = 1;
    boolean isFirst = true;

    for (ImageItem item : result.items()) {
      if (isThumbnail(item, isFirst)) {
        thumbnailKey = item.finalObjectKey();
        isFirst = false;
      } else {
        detailImages.add(new ImageInfo(item.imageId(), item.finalObjectKey(), detailOrder++));
        isFirst = false;
      }
    }

    return new ClassImages(thumbnailKey, detailImages);
  }

  @Override
  public Map<Long, String> loadThumbnailKeys(List<Long> classIds) {
    GetImagesByReferencesResult result =
        getImagesByReferencesUseCase.execute(
            new GetImagesByReferencesCommand(ImageReferenceType.MARKET_CLASS_THUMB, classIds));

    // NOTE: Collectors.toMap() throws NullPointerException when a value is null.
    // Use a plain HashMap to safely store null thumbnails for classes with no
    // images yet.
    Map<Long, String> thumbnailMap = new java.util.HashMap<>();
    for (Long classId : classIds) {
      List<ImageItem> items = result.itemsByReferenceId().getOrDefault(classId, List.of());
      thumbnailMap.put(classId, items.isEmpty() ? null : items.get(0).finalObjectKey());
    }
    return thumbnailMap;
  }

  /**
   * Determines whether an image item is the thumbnail.
   *
   * <p>The image module returns items sorted by {@code img_order} ascending. The thumbnail is
   * always the item with the lowest order (conventionally {@code img_order = 1}). Rather than
   * relying on the fragile object-key path prefix, we treat the first item in the sorted list as
   * the thumbnail ({@code firstItem == true}).
   *
   * <p>If the image module ever changes its sort contract, update this method accordingly.
   */
  private boolean isThumbnail(ImageItem item, boolean isFirst) {
    return isFirst;
  }
}
