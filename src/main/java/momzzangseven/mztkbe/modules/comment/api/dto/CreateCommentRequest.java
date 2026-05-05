package momzzangseven.mztkbe.modules.comment.api.dto;

import jakarta.validation.constraints.NotBlank;
import momzzangseven.mztkbe.modules.comment.application.dto.CreateCommentCommand;

public record CreateCommentRequest(
    @NotBlank(message = "댓글 내용은 필수입니다.") String content, Long parentId) {
  public CreateCommentCommand toCommand(Long postId, Long userId) {
    return new CreateCommentCommand(postId, userId, this.parentId, this.content);
  }

  public CreateCommentCommand toAnswerCommand(Long answerId, Long userId) {
    return CreateCommentCommand.forAnswer(answerId, userId, this.parentId, this.content);
  }
}
