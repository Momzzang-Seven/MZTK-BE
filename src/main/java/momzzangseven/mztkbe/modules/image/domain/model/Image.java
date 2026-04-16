package momzzangseven.mztkbe.modules.image.domain.model;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.image.ImageStatusInvalidException;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;

/**
 * Core domain model representing an uploaded image record. Immutable; state changes return new
 * instances via toBuilder().
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Image {

  private final Long id;
  private final Long userId;
  private final ImageReferenceType referenceType;

  /** Polymorphic reference to the owning entity (post/class/etc.). Null until entity is created. */
  private final Long referenceId;

  private final ImageStatus status;
  private final String tmpObjectKey;
  private final String finalObjectKey;
  private final Integer imgOrder;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final String errorReason;

  /**
   * Factory method for new PENDING images before S3 upload. It doesn't set id field, so that the
   * JPA repository automatically generate id and INSERT it.
   */
  public static Image createPending(
      Long userId, ImageReferenceType referenceType, String tmpObjectKey, int imgOrder) {
    return Image.builder()
        .userId(userId)
        .referenceType(referenceType)
        .referenceId(null)
        .status(ImageStatus.PENDING)
        .tmpObjectKey(tmpObjectKey)
        .finalObjectKey(null)
        .imgOrder(imgOrder)
        .errorReason(null)
        .build();
  }

  /**
   * Transitions the image to COMPLETED status and sets the final S3 object key.
   *
   * @param finalObjectKey S3 key of the converted WebP image
   * @return new Image instance with COMPLETED status
   */
  public Image complete(String finalObjectKey) {
    if (this.status != ImageStatus.PENDING) {
      throw new ImageStatusInvalidException("Cannot complete image with status: " + this.status);
    }
    return toBuilder().status(ImageStatus.COMPLETED).finalObjectKey(finalObjectKey).build();
  }

  /**
   * Transitions the image to FAILED status.
   *
   * @return new Image instance with FAILED status
   */
  public Image fail(String errorReason) {
    if (this.status != ImageStatus.PENDING) {
      throw new ImageStatusInvalidException("Cannot fail image with status: " + this.status);
    }
    return toBuilder().status(ImageStatus.FAILED).errorReason(errorReason).build();
  }

  /** Ensures the image is fully processed before a post flow links it. */
  public void requireCompletedForPostAttach() {
    if (this.status != ImageStatus.COMPLETED) {
      throw new ImageStatusInvalidException(
          "Image must be COMPLETED before attaching to a post: " + this.status);
    }
  }

  /**
   * Updates the reference (owner entity) of this image. Used when linking a newly uploaded image to
   * a post/class, or unlinking during deletion.
   *
   * @param referenceType the type of the owning entity, or null to unlink
   * @param referenceId the ID of the owning entity, or null to unlink
   * @return new Image instance with updated reference fields
   */
  public Image updateReference(ImageReferenceType referenceType, Long referenceId) {
    return toBuilder().referenceType(referenceType).referenceId(referenceId).build();
  }

  /**
   * Updates the display order of this image within its reference context.
   *
   * @param imgOrder 1-based order index
   * @return new Image instance with updated imgOrder
   */
  public Image updateImageOrder(int imgOrder) {
    return toBuilder().imgOrder(imgOrder).build();
  }
}
