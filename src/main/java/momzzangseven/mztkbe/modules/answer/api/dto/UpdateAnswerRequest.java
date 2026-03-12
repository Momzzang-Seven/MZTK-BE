package momzzangseven.mztkbe.modules.answer.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.dto.UpdateAnswerCommand;
import org.hibernate.validator.constraints.URL;

public record UpdateAnswerRequest(
    @NotBlank(message = "수정할 답변 내용은 필수입니다.") String content,
    List<@URL(message = "올바른 이미지 URL 형식이 아닙니다.") String> imageUrls) {
  public UpdateAnswerCommand toCommand(Long postId, Long answerId, Long userId) {
    return new UpdateAnswerCommand(postId, answerId, userId, this.content, this.getSafeImageUrls());
  }

  private List<String> getSafeImageUrls() {
    return imageUrls != null ? imageUrls : List.of();
  }
}
