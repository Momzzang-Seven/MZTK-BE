package momzzangseven.mztkbe.modules.comment.api.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentResult;

public record CommentResponse(
    Long commentId,
    String content,
    Long writerId,
    Long parentId,
    boolean isDeleted,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static CommentResponse from(CommentResult result) {
    return new CommentResponse(
        result.id(),
        result.isDeleted() ? "삭제된 댓글입니다." : result.content(),
        result.isDeleted() ? null : result.writerId(),
        result.parentId(),
        result.isDeleted(),
        result.createdAt(),
        result.updatedAt());
  }
}
