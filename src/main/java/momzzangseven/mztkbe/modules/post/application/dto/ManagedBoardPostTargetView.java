package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

/** Read model for admin moderation decisions on a post. */
public record ManagedBoardPostTargetView(Long postId, PostType type, PostStatus status) {}
