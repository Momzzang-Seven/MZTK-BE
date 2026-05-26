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

  /**
   * 락 없이 게시글을 조회한다. 호출 트랜잭션의 readOnly 플래그를 보지 않는다.
   *
   * <p>본 메서드는 lost-update 윈도우를 닫지 않는다. 동시 변경과 직렬화가 필요한 쓰기 호출자는 명시적으로 {@link
   * #loadPostForUpdate(Long)} 를 사용해야 한다 (MOM-459).
   */
  Optional<Post> loadPost(Long postId);

  /**
   * row-level lock(SELECT … FOR UPDATE)을 걸고 게시글을 조회한다.
   *
   * <p>구현은 동일 트랜잭션에서 {@link #loadPost(Long)} 으로 attach 된 stale managed entity 가 있을 가능성을 고려해 락 획득 직후
   * DB 상태와 강제 재동기화해야 한다 (MOM-459). 그렇지 않으면 SQL 락은 잡혀도 in-memory state 는 Phase 1 snapshot 그대로라
   * lost-update 가드가 무력화된다.
   */
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

  int updateQuestionPublicationStateIfExpected(
      Long postId,
      PostPublicationStatus expectedStatus,
      String expectedCurrentCreateExecutionIntentId,
      String expectedPublicationFailureTerminalStatus,
      String expectedPublicationFailureReason,
      PostPublicationStatus targetStatus,
      String currentCreateExecutionIntentId,
      String publicationFailureTerminalStatus,
      String publicationFailureReason);

  int markQuestionPostSolved(Long postId);
}
