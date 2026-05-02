package momzzangseven.mztkbe.modules.admin.board.application.dto;

import java.time.LocalDateTime;

/** Result row for admin board comments. */
public record AdminBoardCommentResult(
    Long commentId,
    Long postId,
    Long writerId,
    String writerNickname,
    String content,
    Long parentId,
    boolean isDeleted,
    LocalDateTime createdAt) {}
