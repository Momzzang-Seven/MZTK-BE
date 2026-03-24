package momzzangseven.mztkbe.modules.image.application.dto;

import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;

public record GetImagesByReferencesResult(Map<Long, List<ImageItem>> itemsByReferenceId) {

  public static GetImagesByReferencesResult of(Map<Long, List<ImageItem>> itemsByReferenceId) {
    return new GetImagesByReferencesResult(itemsByReferenceId);
  }

  public record ImageItem(Long imageId, ImageStatus status, String finalObjectKey) {

    public static ImageItem from(Image image) {
      return new ImageItem(image.getId(), image.getStatus(), image.getFinalObjectKey());
    }
  }
}
