package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.post.PostInvalidInputException;
import momzzangseven.mztkbe.modules.post.application.port.in.SyncAcceptedAnswerUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadAcceptedAnswerPort;
import momzzangseven.mztkbe.modules.post.application.port.out.MarkAcceptedAnswerPort;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SyncAcceptedAnswerService implements SyncAcceptedAnswerUseCase {

  private final PostPersistencePort postPersistencePort;
  private final LoadAcceptedAnswerPort loadAcceptedAnswerPort;
  private final MarkAcceptedAnswerPort markAcceptedAnswerPort;

  @Override
  @Transactional
  public void beginPendingAccept(Long postId, Long answerId) {
    LoadAcceptedAnswerPort.AcceptedAnswerInfo answer =
        requireAnswerForUpdate(answerId, "qna auto accept");
    Post post = requirePostForUpdate(postId, "qna auto accept");
    validateAnswerBelongsToPost(answer, post);

    postPersistencePort.savePost(post.beginAccept(answerId));
  }

  @Override
  @Transactional
  public void confirmAccepted(Long postId, Long answerId) {
    LoadAcceptedAnswerPort.AcceptedAnswerInfo answer =
        requireAnswerForUpdate(answerId, "qna accept");
    Post post = requirePostForUpdate(postId, "qna accept");
    validateAnswerBelongsToPost(answer, post);

    postPersistencePort.savePost(post.confirmAccepted(answerId));
    markAcceptedAnswerPort.markAccepted(answerId);
  }

  @Override
  @Transactional
  public void rollbackPendingAccept(Long postId, Long answerId) {
    LoadAcceptedAnswerPort.AcceptedAnswerInfo answer =
        requireAnswerForUpdate(answerId, "qna rollback");
    Post post = requirePostForUpdate(postId, "qna rollback");
    validateAnswerBelongsToPost(answer, post);

    postPersistencePort.savePost(post.cancelPendingAccept(answerId));
  }

  private LoadAcceptedAnswerPort.AcceptedAnswerInfo requireAnswerForUpdate(
      Long answerId, String operation) {
    return loadAcceptedAnswerPort
        .loadAcceptedAnswerForUpdate(answerId)
        .orElseThrow(
            () -> new IllegalStateException("missing answer for " + operation + ": " + answerId));
  }

  private Post requirePostForUpdate(Long postId, String operation) {
    return postPersistencePort
        .loadPostForUpdate(postId)
        .orElseThrow(
            () -> new IllegalStateException("missing post for " + operation + ": " + postId));
  }

  private void validateAnswerBelongsToPost(
      LoadAcceptedAnswerPort.AcceptedAnswerInfo answer, Post post) {
    if (!answer.postId().equals(post.getId())) {
      throw new PostInvalidInputException("Answer does not belong to this post.");
    }
  }
}
