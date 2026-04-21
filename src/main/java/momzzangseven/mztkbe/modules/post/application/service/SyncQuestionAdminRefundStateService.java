package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.in.SyncQuestionAdminRefundStateUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SyncQuestionAdminRefundStateService implements SyncQuestionAdminRefundStateUseCase {

  private final PostPersistencePort postPersistencePort;

  @Override
  @Transactional
  public void beginPendingRefund(Long postId) {
    Post post = requirePostForUpdate(postId, "qna admin refund");
    postPersistencePort.savePost(post.beginAdminRefund());
  }

  @Override
  @Transactional
  public void rollbackPendingRefund(Long postId) {
    Post post = requirePostForUpdate(postId, "qna admin refund rollback");
    postPersistencePort.savePost(post.cancelAdminRefund());
  }

  private Post requirePostForUpdate(Long postId, String operation) {
    return postPersistencePort
        .loadPostForUpdate(postId)
        .orElseThrow(
            () -> new IllegalStateException("missing post for " + operation + ": " + postId));
  }
}
