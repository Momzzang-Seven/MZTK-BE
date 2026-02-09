package momzzangseven.mztkbe.modules.post.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.entity.PostEntity;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostPersistenceAdapter implements PostPersistencePort {
  private final PostJpaRepository postJpaRepository;

  @Override
  public Post savePost(Post post) {

    PostEntity entity = PostEntity.fromDomain(post);
    PostEntity savedEntity = postJpaRepository.save(entity);
    return savedEntity.toDomain();
  }

  @Override
  public void deletePost(Post post) {

    postJpaRepository.delete(PostEntity.fromDomain(post));
  }

  @Override
  public Optional<Post> loadPost(Long postId) {

    return postJpaRepository.findById(postId).map(PostEntity::toDomain);
  }
}
