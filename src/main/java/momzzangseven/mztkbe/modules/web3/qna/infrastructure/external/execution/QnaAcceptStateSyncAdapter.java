package momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.execution;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.SaveAnswerPort;
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
    Post post =
        postPersistencePort
            .loadPostForUpdate(postId)
            .orElseThrow(() -> new IllegalStateException("missing post for qna accept: " + postId));
    postPersistencePort.savePost(post.confirmAccepted(answerId));

    saveAnswerPort.saveAnswer(
        loadAnswerPort
            .loadAnswerForUpdate(answerId)
            .orElseThrow(
                () -> new IllegalStateException("missing answer for qna accept: " + answerId))
            .accept());
  }

  @Override
  public void rollbackPendingAccept(Long postId, Long answerId) {
    Post post =
        postPersistencePort
            .loadPostForUpdate(postId)
            .orElseThrow(() -> new IllegalStateException("missing post for qna accept: " + postId));
    postPersistencePort.savePost(post.cancelPendingAccept(answerId));
  }
}
