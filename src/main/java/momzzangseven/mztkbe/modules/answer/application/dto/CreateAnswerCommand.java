package momzzangseven.mztkbe.modules.answer.application.dto;

import java.util.HashSet;
import java.util.List;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;

public record CreateAnswerCommand(Long postId, Long userId, String content, List<Long> imageIds) {

  public CreateAnswerCommand {
    imageIds = imageIds == null ? null : List.copyOf(imageIds);
    validate(postId, userId, content, imageIds);
  }

  public void validate() {
    validate(postId, userId, content, imageIds);
  }

  private static void validate(Long postId, Long userId, String content, List<Long> imageIds) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    if (userId == null) {
      throw new AnswerInvalidInputException("userId is required.");
    }
    if (content == null || content.isBlank()) {
      throw new AnswerInvalidInputException("Answer content must not be blank.");
    }
    if (imageIds != null && new HashSet<>(imageIds).size() != imageIds.size()) {
      throw new AnswerInvalidInputException("Duplicate image IDs are not allowed.");
    }
  }
}
