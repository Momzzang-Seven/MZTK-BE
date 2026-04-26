package momzzangseven.mztkbe.modules.comment.application.dto;

import java.time.LocalDateTime;

public record CommentedPostRef(
    Long postId, Long latestCommentId, LocalDateTime latestCommentedAt) {}
