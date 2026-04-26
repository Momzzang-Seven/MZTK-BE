package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.post.application.dto.PostCursorSearchCondition;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;
import momzzangseven.mztkbe.modules.post.domain.model.Post;

public interface PostPersistencePort {

  Post savePost(Post post);

  Optional<Post> loadPost(Long postId);

  /** row-level lock(SELECT … FOR UPDATE)을 걸고 게시글을 조회한다. */
  Optional<Post> loadPostForUpdate(Long postId);

  void deletePost(Post post);

  boolean existsPost(Long postId);

  List<Post> loadPostsByIdsPreservingOrder(List<Long> postIds);

  List<Post> findPostsByCondition(PostSearchCondition condition, List<Long> filteredPostIds);

  List<Post> findPostsByCursorCondition(PostCursorSearchCondition condition, Long tagId);

  int markQuestionPostSolved(Long postId);
}
