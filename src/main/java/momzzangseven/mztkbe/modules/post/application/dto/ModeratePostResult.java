package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.modules.post.domain.model.PostModerationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;

/** Result for admin-managed post moderation status changes. */
public record ModeratePostResult(
    Long postId,
    boolean moderated,
    PostPublicationStatus publicationStatus,
    PostModerationStatus moderationStatus) {}
