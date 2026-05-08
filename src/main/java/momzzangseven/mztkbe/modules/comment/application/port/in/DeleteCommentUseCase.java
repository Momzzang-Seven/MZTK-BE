package momzzangseven.mztkbe.modules.comment.application.port.in;

import momzzangseven.mztkbe.modules.comment.application.dto.DeleteAnswerCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.dto.DeleteCommentCommand;

public interface DeleteCommentUseCase {
  void deleteComment(DeleteCommentCommand command);

  void deleteAnswerComment(DeleteAnswerCommentCommand command);

  void softDeleteAllCommentsByRootPostId(Long postId);

  void deleteCommentsByAnswerId(Long answerId);
}
