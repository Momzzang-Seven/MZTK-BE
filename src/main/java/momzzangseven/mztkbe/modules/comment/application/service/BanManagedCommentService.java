package momzzangseven.mztkbe.modules.comment.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.comment.CommentNotFoundException;
import momzzangseven.mztkbe.modules.comment.application.dto.BanManagedCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.dto.BanManagedCommentResult;
import momzzangseven.mztkbe.modules.comment.application.port.in.BanManagedCommentUseCase;
import momzzangseven.mztkbe.modules.comment.application.port.out.LoadCommentPort;
import momzzangseven.mztkbe.modules.comment.application.port.out.SaveCommentPort;
import momzzangseven.mztkbe.modules.comment.domain.model.Comment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for admin-managed comment soft delete. */
@Service
@RequiredArgsConstructor
public class BanManagedCommentService implements BanManagedCommentUseCase {

  private final LoadCommentPort loadCommentPort;
  private final SaveCommentPort saveCommentPort;

  @Override
  @Transactional
  public BanManagedCommentResult execute(BanManagedCommentCommand command) {
    command.validate();
    Comment comment =
        loadCommentPort.loadComment(command.commentId()).orElseThrow(CommentNotFoundException::new);
    if (comment.isDeleted()) {
      return new BanManagedCommentResult(comment.getId(), comment.getPostId(), false);
    }

    comment.delete();
    Comment saved = saveCommentPort.saveComment(comment);
    return new BanManagedCommentResult(saved.getId(), saved.getPostId(), true);
  }
}
