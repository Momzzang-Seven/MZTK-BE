package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.modules.post.domain.model.PostModerationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

/** Query for admin board post list reads. */
public record GetManagedBoardPostsQuery(
    String search,
    PostStatus status,
    PostType type,
    PostPublicationStatus publicationStatus,
    PostModerationStatus moderationStatus) {}
