package momzzangseven.mztkbe.modules.comment.application.dto;

public record CreateCommentCommand(Long postId, Long userId, Long parentId, String content) {
  public CreateCommentCommand {
    validate(postId, userId, content);
  }

  private void validate(Long postId, Long userId, String content) {
    if (postId == null) throw new IllegalArgumentException("게시글 ID는 필수입니다.");
    if (userId == null) throw new IllegalArgumentException("사용자 ID는 필수입니다.");
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("댓글 내용은 필수입니다.");
    }
    if (content.length() > 1000) {
      throw new IllegalArgumentException("댓글은 1000자 이내로 작성해주세요.");
    }
  }
}
