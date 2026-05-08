package momzzangseven.mztkbe.modules.comment.api.dto;

import jakarta.validation.constraints.NotBlank;
import momzzangseven.mztkbe.modules.comment.application.dto.UpdateAnswerCommentCommand;

public record UpdateCommentRequest(@NotBlank(message = "수정할 내용은 필수입니다.") String content) {

  public UpdateAnswerCommentCommand toAnswerCommand(Long answerId, Long commentId, Long userId) {
    return new UpdateAnswerCommentCommand(answerId, commentId, userId, content);
  }
}
