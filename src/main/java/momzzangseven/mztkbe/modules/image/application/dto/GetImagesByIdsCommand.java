package momzzangseven.mztkbe.modules.image.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.error.image.ImageMaxCountExceedException;
import momzzangseven.mztkbe.global.error.image.InvalidImageRefTypeException;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageCountPolicy;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;

/** Input command for bulk image metadata lookup by ID list. */
public record GetImagesByIdsCommand(
    Long userId, ImageReferenceType referenceType, Long referenceId, List<Long> ids) {

  /** Validates business rules for the bulk image ID lookup request. */
  public void validate() {
    if (userId == null || userId <= 0) {
      throw new UserNotAuthenticatedException("User ID must be positive");
    }
    if (referenceType == null) {
      throw new InvalidImageRefTypeException("referenceType must not be null");
    }
    if (!referenceType.isRequestFacing()) {
      throw new InvalidImageRefTypeException(
          referenceType + " is an internal reference type and cannot be used in requests.");
    }
    if (referenceId == null || referenceId <= 0) {
      throw new IllegalArgumentException("referenceId must be positive");
    }
    if (ids == null || ids.isEmpty()) {
      throw new IllegalArgumentException("ids must not be empty");
    }
    for (Long id : ids) {
      if (id == null || id <= 0) {
        throw new IllegalArgumentException("ids must be positive");
      }
    }

    int maxCount = ImageCountPolicy.of(referenceType).getMaxCount();
    if (ids.size() > maxCount) {
      throw new ImageMaxCountExceedException(
          "Image ID count exceeds limit: max=" + maxCount + " for " + referenceType);
    }
  }
}
