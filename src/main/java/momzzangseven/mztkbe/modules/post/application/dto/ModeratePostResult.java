package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostModerationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;

/** Result for admin-managed post moderation status changes. */
public record ModeratePostResult(
    Long postId,
    boolean moderated,
    PostPublicationStatus publicationStatus,
    PostModerationStatus moderationStatus,
    boolean publiclyVisible) {

  /** Creates a result while deriving public visibility from the returned status values. */
  public ModeratePostResult(
      Long postId,
      boolean moderated,
      PostPublicationStatus publicationStatus,
      PostModerationStatus moderationStatus) {
    this(
        postId,
        moderated,
        publicationStatus,
        moderationStatus,
        publicationStatus == PostPublicationStatus.VISIBLE
            && moderationStatus == PostModerationStatus.NORMAL);
  }

  /** Creates a result from the post domain visibility policy. */
  public static ModeratePostResult from(Post post, boolean moderated) {
    return new ModeratePostResult(
        post.getId(),
        moderated,
        post.getPublicationStatus(),
        post.getModerationStatus(),
        post.isPubliclyVisible());
  }
}
