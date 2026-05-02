package momzzangseven.mztkbe.modules.comment.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.modules.comment.application.dto.FindCommentedPostRefsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.LatestCommentedPostRef;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LoadCommentPort {
  Optional<Comment> loadComment(Long commentId);

  Page<Comment> loadRootComments(Long postId, Pageable pageable);

  Page<Comment> loadReplies(Long parentId, Pageable pageable);

  List<Comment> loadRootCommentsByCursor(Long postId, CursorPageRequest pageRequest);

  List<Comment> loadRepliesByCursor(Long parentId, CursorPageRequest pageRequest);

  Map<Long, Long> countCommentsByUserIds(List<Long> userIds);

  long countCommentsByPostId(Long postId);

  Map<Long, Long> countCommentsByPostIds(List<Long> postIds);

  Map<Long, Long> countDirectRepliesByParentIds(List<Long> parentIds);

  List<LatestCommentedPostRef> findCommentedPostRefsByUserCursor(FindCommentedPostRefsQuery query);

  /** 하드 딜리트 정책에 따라 삭제 대상을 조회합니다. */
  List<Long> loadCommentIdsForDeletion(LocalDateTime cutoff, int batchSize);
}
