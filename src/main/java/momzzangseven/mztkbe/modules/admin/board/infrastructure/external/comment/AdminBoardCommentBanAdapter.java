package momzzangseven.mztkbe.modules.admin.board.infrastructure.external.comment;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.BanAdminBoardCommentPort;
import momzzangseven.mztkbe.modules.comment.application.dto.BanManagedCommentCommand;
import momzzangseven.mztkbe.modules.comment.application.port.in.BanManagedCommentUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminBoardCommentBanAdapter implements BanAdminBoardCommentPort {

  private final BanManagedCommentUseCase banManagedCommentUseCase;

  @Override
  public BanAdminBoardCommentResult ban(Long commentId) {
    var result = banManagedCommentUseCase.execute(new BanManagedCommentCommand(commentId));
    return new BanAdminBoardCommentResult(result.commentId(), result.postId(), result.moderated());
  }
}
