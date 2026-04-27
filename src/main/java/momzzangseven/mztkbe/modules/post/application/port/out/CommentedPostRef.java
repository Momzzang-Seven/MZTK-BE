package momzzangseven.mztkbe.modules.post.application.port.out;

import java.time.LocalDateTime;

public record CommentedPostRef(
    Long postId, Long latestCommentId, LocalDateTime latestCommentedAt) {}
