package momzzangseven.mztkbe.modules.comment.application.port.out;

import momzzangseven.mztkbe.modules.comment.domain.model.Comment;

public interface SaveCommentPort {
  Comment saveComment(Comment comment);
}
