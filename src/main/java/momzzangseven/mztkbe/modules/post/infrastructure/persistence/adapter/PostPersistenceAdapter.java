package momzzangseven.mztkbe.modules.post.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.post.application.port.out.PostPersistencePort; // 변경된 포트
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.infrastructure.persistence.repository.PostRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostPersistenceAdapter implements PostPersistencePort {

  private final PostRepository postRepository;

  @Override
  public Post savePost(Post post) {
    return postRepository.save(post);
  }

  @Override
  public void deletePost(Post post) {
    postRepository.delete(post);
  }

  @Override
  public Optional<Post> loadPost(Long postId) {
    return postRepository.findById(postId);
  }
}
