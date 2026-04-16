package momzzangseven.mztkbe.modules.image.application.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;

public record GetImagesStatusResult(List<ImageLookupItem> images) {

  public record ImageLookupItem(Long imageId, LookupStatus status) {}

  public enum LookupStatus {
    PENDING,
    COMPLETED,
    FAILED,
    NOT_FOUND;

    public static LookupStatus from(ImageStatus status) {
      return switch (status) {
        case PENDING -> PENDING;
        case COMPLETED -> COMPLETED;
        case FAILED -> FAILED;
      };
    }
  }
}
