package momzzangseven.mztkbe.modules.comment.application.dto;

public record DeleteCommentCommand(Long commentId, Long userId) {
  public DeleteCommentCommand {
    if (commentId == null) throw new IllegalArgumentException("댓글 ID는 필수입니다.");
    if (userId == null) throw new IllegalArgumentException("사용자 ID는 필수입니다.");
  }
}
