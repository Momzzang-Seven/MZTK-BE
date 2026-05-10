package momzzangseven.mztkbe.modules.image.application.dto;

import java.util.List;

public record ReleaseAnswerUpdateImagesCommand(List<Long> updateStateIds) {

  public void validate() {
    if (updateStateIds == null) {
      throw new IllegalArgumentException("updateStateIds must not be null");
    }
    for (Long updateStateId : updateStateIds) {
      if (updateStateId == null || updateStateId <= 0) {
        throw new IllegalArgumentException("updateStateIds must be positive");
      }
    }
  }
}
