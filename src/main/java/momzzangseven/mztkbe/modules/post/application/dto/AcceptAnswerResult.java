package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;

public record AcceptAnswerResult(Long postId, Long acceptedAnswerId, PostStatus status) {

  public static AcceptAnswerResult from(Post post) {
    return new AcceptAnswerResult(post.getId(), post.getAcceptedAnswerId(), post.getStatus());
  }
}
