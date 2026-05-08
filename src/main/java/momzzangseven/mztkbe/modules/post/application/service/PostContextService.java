package momzzangseven.mztkbe.modules.post.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.in.GetPostContextUseCase;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostContextService implements GetPostContextUseCase {

  private final PostPersistencePort postPersistencePort;

  @Override
  @Transactional(readOnly = true)
  public Optional<PostContext> getPostContext(Long postId) {
    return postPersistencePort.loadPost(postId).map(this::toContext);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<PostContext> getPostContextForUpdate(Long postId) {
    return postPersistencePort.loadPostForUpdate(postId).map(this::toContext);
  }

  private PostContext toContext(Post post) {
    return new PostContext(
        post.getId(),
        post.getUserId(),
        post.getIsSolved(),
        PostType.QUESTION.equals(post.getType()),
        post.getContent(),
        post.getReward(),
        post.getStatus() != PostStatus.OPEN,
        post.getStatus(),
        post.getPublicationStatus(),
        post.getModerationStatus(),
        post.getAcceptedAnswerId());
  }
}
