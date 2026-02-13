package momzzangseven.mztkbe.modules.comment.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;

public record CommentResult(
    Long id,
    String content,
    Long writerId,
    Long parentId,
    boolean isDeleted,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {
  public static CommentResult from(Comment comment) {
    return new CommentResult(
        comment.getId(),
        comment.getContent(),
        comment.getWriterId(),
        comment.getParentId(),
        comment.isDeleted(),
        comment.getCreatedAt(),
        comment.getUpdatedAt());
  }
}
