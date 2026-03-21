package momzzangseven.mztkbe.modules.image.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.image.ImageMaxCountExceedException;
import momzzangseven.mztkbe.global.error.image.ImageNotBelongsToUserException;
import momzzangseven.mztkbe.global.error.image.ImageNotFoundException;
import momzzangseven.mztkbe.global.error.image.InvalidImageRefTypeException;
import momzzangseven.mztkbe.modules.image.application.dto.UpsertImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.UpsertImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteImagePort;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteS3ObjectPort;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.application.port.out.UpdateImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageCountPolicy;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that orchestrates image upsert when a reference is edited.
 *
 * <p>Execution phases:
 *
 * <ol>
 *   <li>Delete phase — unlink images removed from the reference; best-effort S3 delete for COMPLETED.
 *   <li>Validate phase — verify ownership, duplicate-link prevention, and count policy.
 *   <li>Order phase — reassign img_order and reference metadata for all retained/new images.
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpsertImagesByReferenceService implements UpsertImagesByReferenceUseCase {
  private final LoadImagePort loadImagePort;
  private final DeleteImagePort deleteImagePort;
  private final DeleteS3ObjectPort deleteS3ObjectPort;
  private final UpdateImagePort updateImagePort;

  @Override
  @Transactional
  public void execute(UpsertImagesByReferenceCommand command) {
    command.validate();

    // ---- Phase 1: Unlink images no longer in the reference ----
    List<Image> existingImages =
        loadImagePort.findImagesByReference(command.referenceType(), command.referenceId());

    Set<Long> retainIds = Set.copyOf(command.imageIds());
    List<Image> toDelete =
        existingImages.stream().filter(img -> !retainIds.contains(img.getId())).toList();

    // Best-effort S3 deletion for COMPLETED images only.
    // PENDING images are intentionally skipped: Lambda may still be processing them,
    // and their tmp S3 objects will be reclaimed by the S3 lifecycle rule.
    for (Image img : toDelete) {
      if (img.getStatus() == ImageStatus.COMPLETED && img.getFinalObjectKey() != null) {
        log.debug(
            "Best-effort S3 delete before unlink: imageId={}, key={}",
            img.getId(),
            img.getFinalObjectKey());
        deleteS3ObjectPort.deleteObject(img.getFinalObjectKey());
      }
    }

    // Unlink images with the reference in the DB
    if (!toDelete.isEmpty()) {
      List<Long> toDeleteIds = toDelete.stream().map(Image::getId).toList();
      deleteImagePort.unlinkImagesByIdIn(toDeleteIds);
    }

    // No images requested: nothing left to link or reorder.
    if (command.imageIds().isEmpty()) {
      return;
    }

    // ---- Phase 2: Validate the final image set ----
    // Acquire a pessimistic write lock to prevent concurrent modification of the same rows.
    List<Image> finalImages = loadImagePort.findImagesByIdInForUpdate(command.imageIds());
    validateOwnership(finalImages, command.userId(), command.referenceId());
    validateCount(command.imageIds().size(), command.referenceType());

    // ---- Phase 3: Reassign order and reference ----
    List<Image> updated = buildOrderedImages(finalImages, command);
    updateImagePort.updateAll(updated);
  }

  /**
   * Verifies that every image in the final set belongs to the requesting user and is not already
   * linked to a different entity.
   *
   * @param images images loaded for the final set
   * @param userId ID of the user performing the update
   * @param referenceId ID of the reference being updated
   */
  private void validateOwnership(List<Image> images, Long userId, Long referenceId) {
    for (Image img : images) {
      // Verify every images that it belongs to the user requested reference update
      if (!userId.equals(img.getUserId())) {
        throw new ImageNotBelongsToUserException(
            "Image " + img.getId() + " does not belong to user " + userId);
      }
      // Verify only retained images that it belongs to the original reference
      if (img.getReferenceId() != null && !referenceId.equals(img.getReferenceId())) {
        throw new InvalidImageRefTypeException(
            "Image " + img.getId() + " is already linked to a different entity");
      }
    }
  }

  /**
   * Verifies that the total image count does not exceed the policy limit for the given type.
   *
   * @param count number of images in the final set
   * @param referenceType reference type used to look up the applicable policy
   */
  private void validateCount(int count, ImageReferenceType referenceType) {
    int maxCount = ImageCountPolicy.of(referenceType).getMaxCount();
    if (count > maxCount) {
      throw new ImageMaxCountExceedException(
          "Image count "
              + count
              + " exceeds the limit of "
              + maxCount
              + " for type "
              + referenceType);
    }
  }

  /**
   * Maps each image to its new order position and reference, preserving the caller-supplied order.
   * List index {@code i} results in {@code img_order = i + 1}.
   *
   * @param images images loaded from DB (maybe in any order)
   * @param command command carrying the desired ordered ID list
   * @return images ready to be bulk-saved
   */
  private List<Image> buildOrderedImages(
      List<Image> images, UpsertImagesByReferenceCommand command) {
    Map<Long, Image> imageById =
        images.stream().collect(Collectors.toMap(Image::getId, img -> img));

    List<Image> result = new ArrayList<>();
    List<Long> orderedIds = command.imageIds();

    for (int i = 0; i < orderedIds.size(); i++) {
      Long id = orderedIds.get(i);
      Image img = imageById.get(id);
      if (img == null) {
        throw new ImageNotFoundException("Requested image not found: id=" + id);
      }
      result.add(
          img.updateReference(command.referenceType(), command.referenceId())
              .updateImageOrder(i + 1));
    }

    return result;
  }
}
