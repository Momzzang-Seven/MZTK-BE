package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
import momzzangseven.mztkbe.modules.answer.domain.model.Answer;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.QnaAcceptStateSyncPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class QnaAcceptStateSyncAdapter implements QnaAcceptStateSyncPort {

  private final PostPersistencePort postPersistencePort;
  private final LoadAnswerPort loadAnswerPort;
  private final SaveAnswerPort saveAnswerPort;

  @Override
  public void confirmAccepted(Long postId, Long answerId) {
    Answer answer = requireAnswerForUpdate(answerId, "qna accept");
    Post post = requirePostForUpdate(postId, "qna accept");
    validateAnswerBelongsToPost(answer, post);

    postPersistencePort.savePost(post.confirmAccepted(answerId));
    saveAnswerPort.saveAnswer(answer.accept());
  }

  @Override
  public void rollbackPendingAccept(Long postId, Long answerId) {
    requireAnswerForUpdate(answerId, "qna rollback");
    Post post = requirePostForUpdate(postId, "qna rollback");
    postPersistencePort.savePost(post.cancelPendingAccept(answerId));
  }

  private Answer requireAnswerForUpdate(Long answerId, String operation) {
    return loadAnswerPort
        .loadAnswerForUpdate(answerId)
        .orElseThrow(
            () -> new IllegalStateException("missing answer for " + operation + ": " + answerId));
  }

  private Post requirePostForUpdate(Long postId, String operation) {
    return postPersistencePort
        .loadPostForUpdate(postId)
        .orElseThrow(
            () -> new IllegalStateException("missing post for " + operation + ": " + postId));
  }

  private void validateAnswerBelongsToPost(Answer answer, Post post) {
    if (!answer.getPostId().equals(post.getId())) {
      throw new PostInvalidInputException("Answer does not belong to this post.");
    }
  }
}
