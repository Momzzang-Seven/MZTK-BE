package momzzangseven.mztkbe.modules.admin.board.api.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardCommentResult;

/** Response row DTO for admin board comments. */
public record AdminBoardCommentResponseDTO(
    Long commentId,
    Long postId,
    Long writerId,
    String writerNickname,
    String content,
    Long parentId,
    boolean isDeleted,
    LocalDateTime createdAt) {

  public static AdminBoardCommentResponseDTO from(AdminBoardCommentResult result) {
    return new AdminBoardCommentResponseDTO(
        result.commentId(),
        result.postId(),
        result.writerId(),
        result.writerNickname(),
        result.content(),
        result.parentId(),
        result.isDeleted(),
        result.createdAt());
  }
}
