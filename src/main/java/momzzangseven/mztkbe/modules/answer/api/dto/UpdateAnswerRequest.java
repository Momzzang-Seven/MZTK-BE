package momzzangseven.mztkbe.modules.answer.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.dto.UpdateAnswerCommand;
import org.hibernate.validator.constraints.URL;

public record UpdateAnswerRequest(
    @NotBlank(message = "Updated answer content is required.") String content,
    List<@URL(message = "Image URL must be a valid URL.") String> imageUrls) {

  public UpdateAnswerCommand toCommand(Long postId, Long answerId, Long userId) {
    return new UpdateAnswerCommand(postId, answerId, userId, content, imageUrls);
  }
}
