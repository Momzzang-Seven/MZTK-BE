package momzzangseven.mztkbe.modules.image.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.auth.UserNotAuthenticatedException;
import momzzangseven.mztkbe.global.error.image.ImageMaxCountExceedException;
import momzzangseven.mztkbe.global.error.image.InvalidImageExtensionException;
import momzzangseven.mztkbe.global.error.image.InvalidImageFileNameException;
import momzzangseven.mztkbe.global.error.image.InvalidImageRefTypeException;
import momzzangseven.mztkbe.modules.image.domain.vo.AllowedImageExtension;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageCountPolicy;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;

/**
 * Input command for the ImageController. Contains validation logic for image count limits and
 * extension whitelist.
 */
public record IssuePresignedUrlCommand(
    Long userId, ImageReferenceType referenceType, List<String> imageFilenames) {

  /** Validates business rules: image count limit and file extension whitelist. */
  public void validate() {
    if (userId == null || userId <= 0) {
      throw new UserNotAuthenticatedException("User ID must be positive");
    }
    // Enum type guarantees referenceType is valid if non-null.
    if (referenceType == null) {
      throw new InvalidImageRefTypeException("referenceType must not be null");
    }
    // MARKET_THUMB and MARKET_DETAIL are internal-only types managed by the server.
    if (!referenceType.isRequestFacing()) {
      throw new InvalidImageRefTypeException(
          referenceType + " is an internal reference type and cannot be used in requests.");
    }
    if (imageFilenames == null || imageFilenames.isEmpty()) {
      throw new InvalidImageFileNameException("imageFilenames must not be empty");
    }

    int maxCount = ImageCountPolicy.of(referenceType).getMaxCount();
    if (imageFilenames.size() > maxCount) {
      throw new ImageMaxCountExceedException(
          "ImageEntity count exceeds limit: max=" + maxCount + " for " + referenceType);
    }

    for (String filename : imageFilenames) {
      if (!AllowedImageExtension.isAllowed(filename)) {
        throw new InvalidImageExtensionException("Unsupported image extension: " + filename + ".");
      }
    }
  }
}
