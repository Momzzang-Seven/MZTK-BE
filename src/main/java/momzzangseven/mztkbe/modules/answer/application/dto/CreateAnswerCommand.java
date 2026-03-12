package momzzangseven.mztkbe.modules.answer.application.dto;

import java.util.List;
import momzzangseven.mztkbe.global.error.answer.AnswerInvalidInputException;

public record CreateAnswerCommand(Long postId, Long userId, String content, List<String> imageUrls) {

  public CreateAnswerCommand {
    imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
    validate(postId, userId, content);
  }

  public void validate() {
    validate(postId, userId, content);
  }

  private static void validate(Long postId, Long userId, String content) {
    if (postId == null) {
      throw new AnswerInvalidInputException("postId is required.");
    }
    if (userId == null) {
      throw new AnswerInvalidInputException("userId is required.");
    }
    if (content == null || content.isBlank()) {
      throw new AnswerInvalidInputException("Answer content must not be blank.");
    }
  }
}
