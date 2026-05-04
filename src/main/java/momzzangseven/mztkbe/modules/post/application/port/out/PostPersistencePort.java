package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.modules.post.application.dto.PostCursorSearchCondition;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;
import momzzangseven.mztkbe.modules.post.domain.model.Post;
import momzzangseven.mztkbe.modules.post.domain.model.PostPublicationStatus;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

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

  List<Post> findPostsByAuthorCursor(
      Long authorId, PostType type, Long tagId, String search, CursorPageRequest pageRequest);

  List<Post> findQuestionPostsForPublicationReconciliation(Long afterPostId, int limit);

  int updateQuestionPublicationStatusIfCurrent(
      Long postId, PostPublicationStatus currentStatus, PostPublicationStatus targetStatus);

  int updateQuestionPublicationStateIfCurrent(
      Long postId,
      PostPublicationStatus currentStatus,
      PostPublicationStatus targetStatus,
      String currentCreateExecutionIntentId,
      String publicationFailureTerminalStatus,
      String publicationFailureReason);

  int markQuestionPostSolved(Long postId);
}
