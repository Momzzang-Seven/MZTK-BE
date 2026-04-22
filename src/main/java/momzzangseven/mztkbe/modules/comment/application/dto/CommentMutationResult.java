package momzzangseven.mztkbe.modules.comment.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;

/** Application result for comment create and update mutations. */
public record CommentMutationResult(
    Long id,
    String content,
    Long writerId,
    Long parentId,
    boolean isDeleted,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  /** Maps a comment domain object into a mutation result. */
  public static CommentMutationResult from(Comment comment) {
    return new CommentMutationResult(
        comment.getId(),
        comment.getContent(),
        comment.getWriterId(),
        comment.getParentId(),
        comment.isDeleted(),
        comment.getCreatedAt(),
        comment.getUpdatedAt());
  }
}
