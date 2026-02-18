package momzzangseven.mztkbe.modules.comment.application.port.in;

import momzzangseven.mztkbe.modules.comment.application.dto.CommentResult;
import momzzangseven.mztkbe.modules.comment.application.dto.UpdateCommentCommand;

public interface UpdateCommentUseCase {
  CommentResult updateComment(UpdateCommentCommand command);
}
