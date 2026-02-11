package momzzangseven.mztkbe.modules.comment.application.port.in;

import momzzangseven.mztkbe.modules.comment.application.dto.CreateCommentCommand;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;

public interface CreateCommentUseCase {
  Comment createComment(CreateCommentCommand command);
}
