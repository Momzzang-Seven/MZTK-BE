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

  /** WHERE is_solved = false 조건으로 게시글을 원자적으로 수정한다. 영향받은 행 수를 반환한다. */
  int updateIfNotSolved(Post post);

  /** WHERE is_solved = false 조건으로 게시글을 원자적으로 삭제한다. 영향받은 행 수를 반환한다. */
  int deleteIfNotSolved(Long postId);
}
