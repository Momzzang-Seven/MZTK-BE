package momzzangseven.mztkbe.modules.admin.board.api.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardCommentSearchResult;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardCommentTargetType;

/** Response row DTO for {@code GET /admin/boards/comments}. */
public record AdminBoardCommentSearchResponseDTO(
    Long commentId,
    Long postId,
    Long answerId,
    Long parentId,
    AdminBoardCommentTargetType targetType,
    Long userId,
    String nickname,
    String content,
    boolean isDeleted,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static AdminBoardCommentSearchResponseDTO from(AdminBoardCommentSearchResult result) {
    return new AdminBoardCommentSearchResponseDTO(
        result.commentId(),
        result.postId(),
        result.answerId(),
        result.parentId(),
        result.targetType(),
        result.userId(),
        result.nickname(),
        result.content(),
        result.isDeleted(),
        result.createdAt(),
        result.updatedAt());
  }
}
