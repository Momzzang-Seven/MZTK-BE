package momzzangseven.mztkbe.modules.post.api.dto;

import momzzangseven.mztkbe.modules.post.application.dto.AcceptAnswerResult;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;

public record AcceptAnswerResponse(Long postId, Long acceptedAnswerId, PostStatus status) {

  public static AcceptAnswerResponse from(AcceptAnswerResult result) {
    return new AcceptAnswerResponse(result.postId(), result.acceptedAnswerId(), result.status());
  }
}
