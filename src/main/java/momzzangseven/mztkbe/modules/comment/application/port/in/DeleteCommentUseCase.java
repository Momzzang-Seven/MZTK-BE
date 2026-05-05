package momzzangseven.mztkbe.modules.comment.application.port.in;

import momzzangseven.mztkbe.modules.comment.application.dto.DeleteCommentCommand;

public interface DeleteCommentUseCase {
  void deleteComment(DeleteCommentCommand command);

  void deleteCommentsByPostId(Long postId);

  void deleteCommentsByAnswerId(Long answerId);
}
