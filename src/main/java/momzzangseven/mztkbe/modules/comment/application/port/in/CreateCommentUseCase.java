package momzzangseven.mztkbe.modules.comment.application.port.in;

import momzzangseven.mztkbe.modules.comment.application.dto.CommentMutationResult;
import momzzangseven.mztkbe.modules.comment.application.dto.CreateCommentCommand;

public interface CreateCommentUseCase {
  CommentMutationResult createComment(CreateCommentCommand command);
}
