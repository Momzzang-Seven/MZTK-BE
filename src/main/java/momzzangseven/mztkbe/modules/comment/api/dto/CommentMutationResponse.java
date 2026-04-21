package momzzangseven.mztkbe.modules.comment.api.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentMutationResult;

/** API response for comment create and update endpoints. */
public record CommentMutationResponse(
    Long commentId,
    String content,
    Long writerId,
    Long parentId,
    boolean isDeleted,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  /** Maps application mutation result into the public comment mutation response. */
  public static CommentMutationResponse from(CommentMutationResult result) {
    return new CommentMutationResponse(
        result.id(),
        result.content(),
        result.writerId(),
        result.parentId(),
        result.isDeleted(),
        result.createdAt(),
        result.updatedAt());
  }
}
