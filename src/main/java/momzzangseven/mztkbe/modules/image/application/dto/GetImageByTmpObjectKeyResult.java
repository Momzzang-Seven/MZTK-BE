package momzzangseven.mztkbe.modules.image.application.dto;

import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;

public record GetImageByTmpObjectKeyResult(
    Long userId, ImageReferenceType referenceType, String tmpObjectKey, String readObjectKey) {

  public static GetImageByTmpObjectKeyResult from(Image image) {
    String readObjectKey =
        image.getFinalObjectKey() == null ? image.getTmpObjectKey() : image.getFinalObjectKey();
    return new GetImageByTmpObjectKeyResult(
        image.getUserId(), image.getReferenceType(), image.getTmpObjectKey(), readObjectKey);
  }
}
