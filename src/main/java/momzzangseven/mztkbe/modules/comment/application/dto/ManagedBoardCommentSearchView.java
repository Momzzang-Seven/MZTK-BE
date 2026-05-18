package momzzangseven.mztkbe.modules.comment.application.dto;

import java.time.LocalDateTime;

/** Read model for admin board global comment search rows. */
public record ManagedBoardCommentSearchView(
    Long commentId,
    Long postId,
    Long answerId,
    Long parentId,
    ManagedBoardCommentTargetType targetType,
    Long writerId,
    String content,
    boolean isDeleted,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
