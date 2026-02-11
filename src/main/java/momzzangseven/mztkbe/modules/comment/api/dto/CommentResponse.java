package momzzangseven.mztkbe.modules.comment.api.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;

public record CommentResponse(
    Long commentId,
    String content,
    Long writerId,
    Long parentId,
    boolean isDeleted,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {
  // [Factory Method]
  public static CommentResponse from(Comment comment) {
    return new CommentResponse(
        comment.getId(),
        comment.isDeleted() ? "삭제된 댓글입니다." : comment.getContent(),
        comment.isDeleted() ? null : comment.getWriterId(),
        comment.getParentId(),
        comment.isDeleted(),
        comment.getCreatedAt(),
        comment.getUpdatedAt());
  }
}
