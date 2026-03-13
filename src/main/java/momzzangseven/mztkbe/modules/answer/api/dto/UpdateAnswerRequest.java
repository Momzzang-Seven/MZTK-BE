package momzzangseven.mztkbe.modules.answer.api.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.answer.application.dto.UpdateAnswerCommand;

public record UpdateAnswerRequest(String content, List<String> imageUrls) {

  public UpdateAnswerCommand toCommand(Long postId, Long answerId, Long userId) {
    return new UpdateAnswerCommand(postId, answerId, userId, content, imageUrls);
  }
}
