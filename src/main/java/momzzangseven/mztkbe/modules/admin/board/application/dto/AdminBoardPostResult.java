package momzzangseven.mztkbe.modules.admin.board.application.dto;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

/** Result row for admin board posts. */
public record AdminBoardPostResult(
    Long postId,
    PostType type,
    PostStatus status,
    String title,
    String contentPreview,
    Long writerId,
    String writerNickname,
    LocalDateTime createdAt,
    long commentCount) {}
