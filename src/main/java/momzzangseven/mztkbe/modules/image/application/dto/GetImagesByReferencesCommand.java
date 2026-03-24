package momzzangseven.mztkbe.modules.image.application.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;

public record GetImagesByReferencesCommand(
    ImageReferenceType referenceType, List<Long> referenceIds) {

  public GetImagesByReferencesCommand {
    referenceIds = referenceIds == null ? List.of() : List.copyOf(referenceIds);
  }

  public void validate() {
    if (referenceType == null) {
      throw new IllegalArgumentException("referenceType must not be null");
    }
    if (referenceIds == null) {
      throw new IllegalArgumentException("referenceIds must not be null");
    }
    if (referenceIds.stream().anyMatch(id -> id == null || id <= 0)) {
      throw new IllegalArgumentException("referenceIds must contain only positive values");
    }
  }
}
