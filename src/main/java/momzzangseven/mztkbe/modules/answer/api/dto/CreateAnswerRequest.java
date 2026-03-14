package momzzangseven.mztkbe.modules.answer.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.dto.CreateAnswerCommand;

public record CreateAnswerRequest(
    @NotBlank(message = "Answer content is required.") String content, List<String> imageUrls) {

  public CreateAnswerCommand toCommand(Long postId, Long userId) {
    return new CreateAnswerCommand(
        postId, userId, content, imageUrls != null ? imageUrls : List.of());
  }
}
