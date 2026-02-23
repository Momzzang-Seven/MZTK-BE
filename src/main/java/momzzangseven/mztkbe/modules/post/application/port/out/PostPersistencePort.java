package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;
import momzzangseven.mztkbe.modules.post.domain.model.Post;

public interface PostPersistencePort {
  Post savePost(Post post);

  Optional<Post> loadPost(Long postId);

  void deletePost(Post post);

  List<Post> findPostsByCondition(PostSearchCondition condition, List<Long> filteredPostIds);

  int markQuestionPostSolved(Long postId);
}
