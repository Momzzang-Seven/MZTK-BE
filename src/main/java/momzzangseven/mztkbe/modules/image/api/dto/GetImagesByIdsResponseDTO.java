package momzzangseven.mztkbe.modules.image.api.dto;

import java.time.Instant;
import java.util.List;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByIdsResult;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByIdsResult.ImageItem;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;

/** Response DTO for GetImagesByIds use case. */
public record GetImagesByIdsResponseDTO(List<ImageItemDTO> images) {

  /** Factory method converting application layer result to response DTO. */
  public static GetImagesByIdsResponseDTO from(GetImagesByIdsResult result) {
    List<ImageItemDTO> images = result.images().stream().map(ImageItemDTO::from).toList();
    return new GetImagesByIdsResponseDTO(images);
  }

  /** A single image entry in the bulk response. */
  public record ImageItemDTO(
      Long imageId,
      Long userId,
      ImageReferenceType referenceType,
      Long referenceId,
      ImageStatus status,
      String finalObjectKey,
      int imgOrder,
      Instant createdAt,
      Instant updatedAt) {

    /** Creates an {@link ImageItemDTO} from an application-layer {@link ImageItem}. */
    public static ImageItemDTO from(ImageItem item) {
      return new ImageItemDTO(
          item.imageId(),
          item.userId(),
          item.referenceType(),
          item.referenceId(),
          item.status(),
          item.finalObjectKey(),
          item.imgOrder(),
          item.createdAt(),
          item.updatedAt());
    }
  }
}
