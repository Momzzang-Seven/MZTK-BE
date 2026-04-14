package momzzangseven.mztkbe.modules.answer.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.dto.UpdateAnswerCommand;

public record UpdateAnswerRequest(
    String content,
    List<
            @NotNull(message = "Image ID must not be null.")
            @Positive(message = "Image ID must be positive.") Long>
        imageIds) {

  public UpdateAnswerCommand toCommand(Long postId, Long answerId, Long userId) {
    return new UpdateAnswerCommand(postId, answerId, userId, content, imageIds);
  }
}
