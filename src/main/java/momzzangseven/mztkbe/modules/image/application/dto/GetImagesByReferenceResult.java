package momzzangseven.mztkbe.modules.image.application.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;

/**
 * Output result of {@link
 * momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByReferenceUseCase}.
 */
public record GetImagesByReferenceResult(List<ImageItem> items) {

  public static GetImagesByReferenceResult of(List<ImageItem> items) {
    return new GetImagesByReferenceResult(items);
  }

  /**
   * A single image entry. {@code finalObjectKey} is {@code null} when {@code status} is PENDING or
   * FAILED.
   */
  public record ImageItem(Long imageId, ImageStatus status, String finalObjectKey) {

    public static ImageItem from(Image image) {
      return new ImageItem(image.getId(), image.getStatus(), image.getFinalObjectKey());
    }
  }
}
