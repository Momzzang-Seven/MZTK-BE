package momzzangseven.mztkbe.modules.image.application.dto;

import java.time.Instant;
import java.util.List;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;

/**
 * Output result of {@link
 * momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByIdsUseCase}.
 */
public record GetImagesByIdsResult(List<ImageItem> images) {

  /**
   * A single image entry. {@code imageUrl} is {@code null} when {@code status} is PENDING or
   * FAILED.
   */
  public record ImageItem(
      Long imageId,
      Long userId,
      ImageReferenceType referenceType,
      Long referenceId,
      ImageStatus status,
      String imageUrl,
      int imgOrder,
      Instant createdAt,
      Instant updatedAt) {

    /** Creates an {@link ImageItem} from a domain {@link Image} object. imageUrl is pre-built. */
    public static ImageItem from(Image image, String imageUrl) {
      return new ImageItem(
          image.getId(),
          image.getUserId(),
          image.getReferenceType().toRequestFacing(),
          image.getReferenceId(),
          image.getStatus(),
          imageUrl,
          image.getImgOrder(),
          image.getCreatedAt(),
          image.getUpdatedAt());
    }
  }
}
