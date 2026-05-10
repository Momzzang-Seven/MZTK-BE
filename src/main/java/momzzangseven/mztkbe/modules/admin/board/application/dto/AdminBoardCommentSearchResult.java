package momzzangseven.mztkbe.modules.admin.board.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardCommentTargetType;

/** Result row for admin board global comment search. */
public record AdminBoardCommentSearchResult(
    Long commentId,
    Long postId,
    Long answerId,
    AdminBoardCommentTargetType targetType,
    Long userId,
    String nickname,
    String content,
    boolean isDeleted,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
