package momzzangseven.mztkbe.modules.comment.application.port.in;

import momzzangseven.mztkbe.modules.comment.application.dto.CommentMutationResult;
import momzzangseven.mztkbe.modules.comment.application.dto.UpdateAnswerCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.dto.UpdateCommentCommand;

public interface UpdateCommentUseCase {
  CommentMutationResult updateComment(UpdateCommentCommand command);

  CommentMutationResult updateAnswerComment(UpdateAnswerCommentCommand command);
}
