package momzzangseven.mztkbe.modules.answer.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;

public record UpdateAnswerCommand(
    Long postId, Long answerId, Long userId, String content, List<String> imageUrls) {

  public UpdateAnswerCommand {
    imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
    validate(postId, answerId, userId, content);
  }

  public void validate() {
    validate(postId, answerId, userId, content);
  }

  private static void validate(Long postId, Long answerId, Long userId, String content) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    if (answerId == null) {
      throw new AnswerInvalidInputException("answerId is required.");
    }
    if (userId == null) {
      throw new AnswerInvalidInputException("userId is required.");
    }
    if (content == null || content.isBlank()) {
      throw new AnswerInvalidInputException("Updated content must not be blank.");
    }
  }
}
