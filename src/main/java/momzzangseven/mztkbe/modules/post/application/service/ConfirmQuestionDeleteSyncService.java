package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.in.ConfirmQuestionDeleteSyncUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.event.PostDeletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the local hard-delete side effects that run after a confirmed on-chain question delete.
 *
 * <p>The service is intentionally idempotent so repeated confirmation events do not fail when the
 * local post row has already been removed.
 */
@Service
@RequiredArgsConstructor
public class ConfirmQuestionDeleteSyncService implements ConfirmQuestionDeleteSyncUseCase {

  private final PostPersistencePort postPersistencePort;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional
  public void confirmDeleted(Long postId) {
    postPersistencePort
        .loadPostForUpdate(postId)
        .ifPresent(
            post -> {
              postPersistencePort.deletePost(post);
              eventPublisher.publishEvent(new PostDeletedEvent(postId, post.getType()));
            });
  }
}
