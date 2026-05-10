package momzzangseven.mztkbe.modules.post.application.port.in;

import java.util.Optional;
import momzzangseven.mztkbe.modules.post.domain.model.PostModerationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;

public interface GetPostContextUseCase {

  Optional<PostContext> getPostContext(Long postId);

  Optional<PostContext> getPostContextForUpdate(Long postId);

  record PostContext(
      Long postId,
      Long writerId,
      boolean solved,
      boolean questionPost,
      String content,
      Long reward,
      boolean answerLocked,
      PostStatus status,
      PostPublicationStatus publicationStatus,
      PostModerationStatus moderationStatus,
      Long acceptedAnswerId) {

    public PostContext(Long postId, Long writerId, boolean solved, boolean questionPost) {
      this(
          postId,
          writerId,
          solved,
          questionPost,
          null,
          null,
          solved,
          null,
          PostPublicationStatus.VISIBLE,
          PostModerationStatus.NORMAL,
          null);
    }

    public PostContext(
        Long postId,
        Long writerId,
        boolean solved,
        boolean questionPost,
        String content,
        Long reward) {
      this(
          postId,
          writerId,
          solved,
          questionPost,
          content,
          reward,
          solved,
          null,
          PostPublicationStatus.VISIBLE,
          PostModerationStatus.NORMAL,
          null);
    }

    public PostContext(
        Long postId,
        Long writerId,
        boolean solved,
        boolean questionPost,
        String content,
        Long reward,
        boolean answerLocked) {
      this(
          postId,
          writerId,
          solved,
          questionPost,
          content,
          reward,
          answerLocked,
          null,
          PostPublicationStatus.VISIBLE,
          PostModerationStatus.NORMAL,
          null);
    }

    public PostContext(
        Long postId,
        Long writerId,
        boolean solved,
        boolean questionPost,
        String content,
        Long reward,
        boolean answerLocked,
        PostStatus status,
        Long acceptedAnswerId) {
      this(
          postId,
          writerId,
          solved,
          questionPost,
          content,
          reward,
          answerLocked,
          status,
          PostPublicationStatus.VISIBLE,
          PostModerationStatus.NORMAL,
          acceptedAnswerId);
    }

    public boolean publiclyVisible() {
      return publicationStatus == PostPublicationStatus.VISIBLE
          && moderationStatus == PostModerationStatus.NORMAL;
    }

    public boolean ownedBy(Long requesterUserId) {
      return requesterUserId != null && writerId.equals(requesterUserId);
    }
  }
}
