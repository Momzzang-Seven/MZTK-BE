package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.post.domain.model.Post;

public interface PostPersistencePort {
  Post savePost(Post post);

  Optional<Post> loadPost(Long postId);

  void deletePost(Post post);
}
