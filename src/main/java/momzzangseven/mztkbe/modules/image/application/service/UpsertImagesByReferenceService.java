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
 *   <li>Delete phase — unlink images removed from the reference; best-effort S3 delete for
 *       COMPLETED.
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

    // ---- Phase 0: Lock the entire reference upfront ----
    // Acquiring SELECT FOR UPDATE on ALL existing images for prevent the cross=transaction
    // deadlock.
    List<Image> existingImages =
        loadImagePort.findImagesByReferenceForUpdate(
            command.referenceType().expand(), command.referenceId());

    Set<Long> retainIds = Set.copyOf(command.imageIds());
    List<Image> toDelete =
        existingImages.stream().filter(img -> !retainIds.contains(img.getId())).toList();

    // ---- Phase 1: Validate the final image set ----
    // findImagesByIdInForUpdate locks any NEW images being added (not yet in the reference).
    // Re-acquiring locks on already-locked rows (the retained subset of existingImages)
    // is a no-op in PostgreSQL — the session already holds the row lock.
    List<Image> finalImages;
    if (command.imageIds().isEmpty()) {
      finalImages = List.of();
    } else {
      finalImages = loadImagePort.findImagesByIdInForUpdate(command.imageIds());
      validateOwnership(
          finalImages, command.userId(), command.referenceType(), command.referenceId());
      validateCount(command.imageIds().size(), command.referenceType());
      validateMarketOrder(finalImages, command.referenceType(), command.imageIds());
    }

    // ---- Phase 2: DB mutations ----
    // Placing DB operations first ensures a DB failure triggers a transaction rollback
    // before any S3 objects are deleted.
    if (!toDelete.isEmpty()) {
      List<Long> toDeleteIds = toDelete.stream().map(Image::getId).toList();
      deleteImagePort.unlinkImagesByIdIn(toDeleteIds);
    }

    if (!finalImages.isEmpty()) {
      List<Image> updated = buildOrderedImages(finalImages, command);
      updateImagePort.updateAll(updated);
    }

    // ---- Phase 3: S3 deletion (best-effort, after all DB mutations) ----
    // PENDING images are skipped — Lambda may still be processing them, and their tmp objects
    // are reclaimed by the S3 lifecycle rule.
    for (Image img : toDelete) {
      if (img.getStatus() == ImageStatus.COMPLETED && img.getFinalObjectKey() != null) {
        log.debug(
            "Best-effort S3 delete after DB unlink: imageId={}, key={}",
            img.getId(),
            img.getFinalObjectKey());
        deleteS3ObjectPort.deleteObject(img.getFinalObjectKey());
      }
    }
  }

  /**
   * Verifies that every image in the final set satisfies three invariants:
   *
   * <ol>
   *   <li><b>Ownership</b> — the image must belong to the requesting user.
   *   <li><b>Reference-type compatibility</b> — the image's {@code referenceType} must fall within
   *       the expanded type family of the command. This applies to fresh PENDING images (type set
   *       at issuance) and UNLINKED images (type preserved from the original issuance) alike. An
   *       image with a {@code null} referenceType does not pass validation and throws an exception.
   *   <li><b>Reference-id exclusivity</b> — if the image is already linked to an entity ({@code
   *       referenceId != null}), it must be linked to exactly this reference; linking a
   *       fully-linked image to a different entity is rejected.
   * </ol>
   *
   * @param images images loaded for the final set
   * @param userId ID of the user performing the update
   * @param referenceType the command's reference type (could be virtual)
   * @param referenceId ID of the reference being updated
   */
  private void validateOwnership(
      List<Image> images, Long userId, ImageReferenceType referenceType, Long referenceId) {
    List<ImageReferenceType> expandedTypes = referenceType.expand();
    for (Image img : images) {
      // (1) Ownership
      if (!userId.equals(img.getUserId())) {
        throw new ImageNotBelongsToUserException("Image does not belong to user");
      }

      // (2) Reference-type should not be null.
      if (img.getReferenceType() == null) {
        throw new InvalidImageRefTypeException("Image reference Type should not be null");
      }
      // (2) Reference-type compatibility (covers both PENDING and unlinked images)
      // expand() resolves virtual types (e.g. MARKET_CLASS → [THUMB, DETAIL]).
      if (img.getReferenceType() != null && !expandedTypes.contains(img.getReferenceType())) {
        throw new InvalidImageRefTypeException(
            "New image has different reference type with command");
      }
      // (3) Reference-id exclusivity: reject if the image is already owned by a different entity
      // If referenceId == null, it is treated as PENDING or UNLINKED
      if (img.getReferenceId() != null && !referenceId.equals(img.getReferenceId())) {
        throw new InvalidImageRefTypeException("Image is already linked to a different entity");
      }
    }
  }

  /**
   * For virtual MARKET types, verifies that the ordered image set satisfies the layout rule: the
   * first image must be the THUMB subtype and every subsequent image must be the DETAIL subtype.
   *
   * <p>Non-virtual types (e.g. {@code COMMUNITY_*}) are skipped entirely.
   *
   * @param images unordered images loaded for the final set
   * @param referenceType the command's reference type (may be virtual)
   * @param orderedIds caller-supplied ordered list of image IDs
   * @throws InvalidImageRefTypeException If this excpetion was thrown, it means issuing pre-signed
   *     url phase had a bug.
   */
  private void validateMarketOrder(
      List<Image> images, ImageReferenceType referenceType, List<Long> orderedIds) {
    if (!referenceType.isVirtual()) {
      return;
    }

    List<ImageReferenceType> expanded = referenceType.expand();
    ImageReferenceType thumbType = expanded.get(0);
    ImageReferenceType detailType = expanded.get(1);

    Map<Long, Image> imageById =
        images.stream().collect(Collectors.toMap(Image::getId, img -> img));

    for (int i = 0; i < orderedIds.size(); i++) {
      Image img = imageById.get(orderedIds.get(i));
      if (img == null) {
        continue; // null case is caught later in buildOrderedImages
      }
      ImageReferenceType expectedType = (i == 0) ? thumbType : detailType;
      if (!expectedType.equals(img.getReferenceType())) {
        throw new InvalidImageRefTypeException(
            "Image "
                + img.getId()
                + " at position "
                + (i + 1)
                + " has type "
                + img.getReferenceType()
                + " but expected "
                + expectedType
                + " for reference type "
                + referenceType);
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
      // For virtual types (MARKET_CLASS, MARKET_STORE), the concrete subtype (THUMB/DETAIL)
      // is already stored on the image from IssuePresignedUrlService. Preserve it so we do
      // not overwrite a concrete type with its virtual parent in the DB.
      ImageReferenceType concreteType =
          command.referenceType().isVirtual() ? img.getReferenceType() : command.referenceType();
      result.add(img.updateReference(concreteType, command.referenceId()).updateImageOrder(i + 1));
    }

    return result;
  }
}
