package momzzangseven.mztkbe.modules.image.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.error.image.InvalidImageRefTypeException;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;

public record ValidatePostAttachableImagesCommand(
    Long userId, Long referenceId, ImageReferenceType referenceType, List<Long> imageIds) {

  public void validate() {
    if (userId == null || userId <= 0) {
      throw new UserNotAuthenticatedException();
    }
    if (referenceId != null && referenceId <= 0) {
      throw new IllegalArgumentException("referenceId must be positive");
    }
    if (referenceType != ImageReferenceType.COMMUNITY_FREE
        && referenceType != ImageReferenceType.COMMUNITY_QUESTION) {
      throw new InvalidImageRefTypeException(
          "Post attach validation supports community post image types only");
    }
    if (imageIds == null || imageIds.isEmpty()) {
      throw new IllegalArgumentException("imageIds must not be empty");
    }
    for (Long imageId : imageIds) {
      if (imageId == null || imageId <= 0) {
        throw new IllegalArgumentException("imageIds must be positive");
      }
    }
  }
}
