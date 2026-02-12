package momzzangseven.mztkbe.modules.comment.application.dto;

public record UpdateCommentCommand(Long commentId, Long userId, String content) {
  public UpdateCommentCommand {
    validate(commentId, userId, content);
  }

  private void validate(Long commentId, Long userId, String content) {
    if (commentId == null) throw new IllegalArgumentException("댓글 ID는 필수입니다.");
    if (userId == null) throw new IllegalArgumentException("사용자 ID는 필수입니다.");
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("수정할 내용은 필수입니다.");
    }
    if (content.length() > 1000) {
      throw new IllegalArgumentException("댓글은 1000자 이내로 작성해주세요.");
    }
  }
}
