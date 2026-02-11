package momzzangseven.mztkbe.modules.comment.application.port.in;

import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GetCommentUseCase {
  Page<Comment> getRootComments(Long postId, Pageable pageable);

  Page<Comment> getReplies(Long parentId, Pageable pageable);
}
