package momzzangseven.mztkbe.modules.comment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size; // [New] 추가
import momzzangseven.mztkbe.modules.comment.application.dto.CreateCommentCommand;

public record CreateCommentRequest(
    @NotBlank(message = "댓글 내용은 필수입니다.") @Size(max = 1000, message = "댓글은 1000자 이내로 작성해주세요.")
        String content,
    Long parentId) {
  public CreateCommentCommand toCommand(Long postId, Long userId) {
    return new CreateCommentCommand(postId, userId, this.parentId, this.content);
  }
}
