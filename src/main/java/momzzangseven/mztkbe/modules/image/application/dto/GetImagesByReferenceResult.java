package momzzangseven.mztkbe.modules.image.application.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.image.domain.model.Image;

/**
 * Output result of {@link
 * momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByReferenceUseCase}.
 */
public record GetImagesByReferenceResult(List<ImageItem> items) {

  public static GetImagesByReferenceResult of(List<ImageItem> items) {
    return new GetImagesByReferenceResult(items);
  }

  /** A single image entry: database ID and its final S3 object key. */
  public record ImageItem(Long imageId, String finalObjectKey) {

    public static ImageItem from(Image image) {
      return new ImageItem(image.getId(), image.getFinalObjectKey());
    }
  }
}
