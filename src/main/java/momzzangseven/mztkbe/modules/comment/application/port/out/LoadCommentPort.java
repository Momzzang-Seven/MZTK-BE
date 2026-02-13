package momzzangseven.mztkbe.modules.comment.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LoadCommentPort {
  Optional<Comment> loadComment(Long commentId);

  Page<Comment> loadRootComments(Long postId, Pageable pageable);

  Page<Comment> loadReplies(Long parentId, Pageable pageable);

  /** 하드 딜리트 정책에 따라 삭제 대상을 조회합니다. */
  List<Long> loadCommentIdsForDeletion(LocalDateTime cutoff, int batchSize);
}
