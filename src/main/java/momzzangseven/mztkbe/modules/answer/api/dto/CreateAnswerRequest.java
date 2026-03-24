package momzzangseven.mztkbe.modules.answer.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerCommand;

public record CreateAnswerRequest(
    @NotBlank(message = "Answer content is required.") String content,
    List<
            @NotNull(message = "Image ID must not be null.")
            @Positive(message = "Image ID must be positive.") Long>
        imageIds) {

  public CreateAnswerCommand toCommand(Long postId, Long userId) {
    return new CreateAnswerCommand(postId, userId, content, imageIds);
  }
}
