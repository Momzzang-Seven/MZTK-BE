package momzzangseven.mztkbe.modules.admin.board.api.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardPostResult;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostModerationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostPublicationStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostType;

/** Response row DTO for admin board posts. */
public record AdminBoardPostResponseDTO(
    Long postId,
    AdminBoardPostType type,
    AdminBoardPostStatus status,
    AdminBoardPostPublicationStatus publicationStatus,
    AdminBoardPostModerationStatus moderationStatus,
    String title,
    String contentPreview,
    Long writerId,
    String writerNickname,
    LocalDateTime createdAt,
    long commentCount) {

  public static AdminBoardPostResponseDTO from(AdminBoardPostResult result) {
    return new AdminBoardPostResponseDTO(
        result.postId(),
        result.type(),
        result.status(),
        result.publicationStatus(),
        result.moderationStatus(),
        result.title(),
        result.contentPreview(),
        result.writerId(),
        result.writerNickname(),
        result.createdAt(),
        result.commentCount());
  }
}
