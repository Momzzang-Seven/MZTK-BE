package momzzangseven.mztkbe.modules.comment.api.dto;

import jakarta.validation.constraints.NotBlank;
import momzzangseven.mztkbe.modules.comment.application.dto.CreateCommentCommand;

public record CreateCommentRequest(
    @NotBlank(message = "댓글 내용은 필수입니다.") String content, Long parentId) {

  // [Convenience Method]
  public CreateCommentCommand toCommand(Long postId, Long writerId) {
    return new CreateCommentCommand(postId, writerId, this.parentId, this.content);
  }
}
