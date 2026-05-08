package momzzangseven.mztkbe.modules.image.application.dto;

import java.util.List;

public record ReserveAnswerUpdateImagesCommand(
    Long updateStateId, Long userId, Long answerId, List<Long> imageIds) {

  public void validate() {
    validatePositive(updateStateId, "updateStateId");
    validatePositive(userId, "userId");
    validatePositive(answerId, "answerId");
    if (imageIds == null) {
      throw new IllegalArgumentException("imageIds must not be null");
    }
  }

  private void validatePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException(fieldName + " must be positive");
    }
  }
}
