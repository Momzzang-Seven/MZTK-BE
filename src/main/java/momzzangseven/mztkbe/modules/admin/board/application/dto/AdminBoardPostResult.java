package momzzangseven.mztkbe.modules.admin.board.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostStatus;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardPostType;

/** Result row for admin board posts. */
public record AdminBoardPostResult(
    Long postId,
    AdminBoardPostType type,
    AdminBoardPostStatus status,
    String title,
    String contentPreview,
    Long writerId,
    String writerNickname,
    LocalDateTime createdAt,
    long commentCount) {}
