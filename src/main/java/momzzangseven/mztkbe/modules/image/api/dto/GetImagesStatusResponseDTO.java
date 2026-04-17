package momzzangseven.mztkbe.modules.image.api.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesStatusResult;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesStatusResult.ImageLookupItem;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesStatusResult.LookupStatus;

public record GetImagesStatusResponseDTO(List<ImageItemDTO> images) {

  public static GetImagesStatusResponseDTO from(GetImagesStatusResult result) {
    return new GetImagesStatusResponseDTO(
        result.images().stream().map(ImageItemDTO::from).toList());
  }

  public record ImageItemDTO(Long imageId, LookupStatus status) {
    public static ImageItemDTO from(ImageLookupItem item) {
      return new ImageItemDTO(item.imageId(), item.status());
    }
  }
}
