package momzzangseven.mztkbe.modules.comment.application.dto;

import java.time.LocalDateTime;

/** Read model for admin board comment rows. */
public record ManagedBoardCommentView(
    Long commentId,
    Long postId,
    Long writerId,
    String content,
    Long parentId,
    boolean isDeleted,
    LocalDateTime createdAt) {}
