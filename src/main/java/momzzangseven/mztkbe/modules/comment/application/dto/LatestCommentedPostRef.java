package momzzangseven.mztkbe.modules.comment.application.dto;

import java.time.LocalDateTime;

public record LatestCommentedPostRef(
    Long postId, Long latestCommentId, LocalDateTime latestCommentedAt) {}
