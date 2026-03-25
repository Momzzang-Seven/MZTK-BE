package momzzangseven.mztkbe.modules.image.application.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;

/** Command for getting images by many references of the same type. */
public record GetImagesByReferencesCommand(
    ImageReferenceType referenceType, List<Long> referenceIds) {

  public GetImagesByReferencesCommand {
    referenceIds =
        referenceIds == null ? null : Collections.unmodifiableList(new ArrayList<>(referenceIds));
  }

  public void validate() {
    if (referenceType == null) {
      throw new IllegalArgumentException("referenceType must not be null");
    }
    if (referenceIds == null) {
      throw new IllegalArgumentException("referenceIds must not be null");
    }
    for (Long referenceId : referenceIds) {
      if (referenceId == null || referenceId <= 0) {
        throw new IllegalArgumentException("referenceIds must be positive");
      }
    }
  }
}
