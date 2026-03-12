package momzzangseven.mztkbe.modules.image.domain.model;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
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

  /** Factory method for new PENDING images before S3 upload. */
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
        .build();
  }
}
