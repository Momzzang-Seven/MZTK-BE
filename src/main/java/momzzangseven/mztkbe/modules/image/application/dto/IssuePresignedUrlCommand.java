package momzzangseven.mztkbe.modules.image.application.dto;

import java.util.List;
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
      throw new IllegalArgumentException("User ID must be positive");
    }
    // Enum type forces the refrenceType must be one of ImageReferenceType.
    if (referenceType == null) {
      throw new IllegalArgumentException("referenceType must not be null");
    }
    if (imageFilenames == null || imageFilenames.isEmpty()) {
      throw new IllegalArgumentException("imageFilenames must not be empty");
    }

    int maxCount = ImageCountPolicy.of(referenceType).getMaxCount();
    if (imageFilenames.size() > maxCount) {
      throw new IllegalArgumentException(
          "ImageEntity count exceeds limit: max=" + maxCount + " for " + referenceType);
    }

    for (String filename : imageFilenames) {
      if (!AllowedImageExtension.isAllowed(filename)) {
        throw new IllegalArgumentException("Unsupported image extension: " + filename + ".");
      }
    }
  }
}
