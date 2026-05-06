package momzzangseven.mztkbe.modules.post.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

/** Read model for admin board post list rows. */
public record ManagedBoardPostView(
    Long postId,
    PostType type,
    PostStatus status,
    String title,
    String content,
    Long writerId,
    LocalDateTime createdAt) {}
