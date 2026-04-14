package momzzangseven.mztkbe.modules.post.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.in.DeleteAnswerLikesUseCase;
import momzzangseven.mztkbe.modules.post.application.port.in.DeletePostLikesUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.PostLikePersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.PostLikeTargetType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostLikeCleanupService implements DeletePostLikesUseCase, DeleteAnswerLikesUseCase {

  private final PostLikePersistencePort postLikePersistencePort;

  @Override
  @Transactional
  public void deletePostLikes(Long postId) {
    postLikePersistencePort.deleteByTarget(PostLikeTargetType.POST, postId);
  }

  @Override
  @Transactional
  public void deleteAnswerLikes(Long answerId) {
    postLikePersistencePort.deleteByTarget(PostLikeTargetType.ANSWER, answerId);
  }
}
