package momzzangseven.mztkbe.modules.image.application.dto;

import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;

/**
 * Command for unlinking all images attached to a specific reference entity. Constructed by event
 * handlers when a post or class is deleted.
 */
public record UnlinkImagesByReferenceCommand(ImageReferenceType referenceType, Long referenceId) {

  public void validate() {
    if (referenceType == null) throw new IllegalArgumentException("referenceType must not be null");
    if (referenceId == null || referenceId <= 0)
      throw new IllegalArgumentException("referenceId must be positive");
  }
}
