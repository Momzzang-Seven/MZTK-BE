package momzzangseven.mztkbe.modules.comment.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LoadCommentPort {
  Optional<Comment> loadComment(Long commentId);

  Page<Comment> loadRootComments(Long postId, Pageable pageable);

  Page<Comment> loadReplies(Long parentId, Pageable pageable);
}
