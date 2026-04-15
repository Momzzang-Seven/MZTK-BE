package momzzangseven.mztkbe.modules.post.application.dto;

import momzzangseven.mztkbe.modules.post.application.port.out.QuestionExecutionWriteView;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;

public record AcceptAnswerResult(
    Long postId, Long acceptedAnswerId, PostStatus status, QuestionExecutionWriteView web3) {

  public static AcceptAnswerResult from(Post post, QuestionExecutionWriteView web3) {
    return new AcceptAnswerResult(post.getId(), post.getAcceptedAnswerId(), post.getStatus(), web3);
  }
}
