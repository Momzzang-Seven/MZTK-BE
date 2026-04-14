package momzzangseven.mztkbe.modules.answer.application.dto;

import java.util.HashSet;
import java.util.List;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;

public record UpdateAnswerCommand(
    Long postId, Long answerId, Long userId, String content, List<Long> imageIds) {

  public UpdateAnswerCommand {
    imageIds = imageIds == null ? null : List.copyOf(imageIds);
    validate(postId, answerId, userId, content, imageIds);
  }

  public void validate() {
    validate(postId, answerId, userId, content, imageIds);
  }

  private static void validate(
      Long postId, Long answerId, Long userId, String content, List<Long> imageIds) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    if (answerId == null) {
      throw new AnswerInvalidInputException("answerId is required.");
    }
    if (userId == null) {
      throw new AnswerInvalidInputException("userId is required.");
    }
    if (content == null && imageIds == null) {
      throw new AnswerInvalidInputException("At least one field must be provided for update.");
    }
    if (content != null && content.isBlank()) {
      throw new AnswerInvalidInputException("Updated content must not be blank.");
    }
    if (imageIds != null && new HashSet<>(imageIds).size() != imageIds.size()) {
      throw new AnswerInvalidInputException("Duplicate image IDs are not allowed.");
    }
  }
}
