package momzzangseven.mztkbe.modules.image.application.dto;

import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;

/** Command for getting images by reference. */
public record GetImagesByReferenceCommand(ImageReferenceType referenceType, Long referenceId) {
  public void validate() {
    if (referenceType == null) throw new IllegalArgumentException("referenceType must not be null");
    if (referenceId == null || referenceId <= 0)
      throw new IllegalArgumentException("referenceId must be positive");
  }
}
