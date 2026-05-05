package momzzangseven.mztkbe.modules.admin.board.infrastructure.external.comment;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostCommentsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostCommentsPort;
import momzzangseven.mztkbe.modules.comment.application.dto.GetManagedBoardPostCommentsQuery;
import momzzangseven.mztkbe.modules.comment.application.port.in.GetManagedBoardPostCommentsUseCase;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminBoardPostCommentReadAdapter implements LoadAdminBoardPostCommentsPort {

  private final GetManagedBoardPostCommentsUseCase getManagedBoardPostCommentsUseCase;

  @Override
  public Page<AdminBoardCommentView> load(GetAdminBoardPostCommentsCommand command) {
    return getManagedBoardPostCommentsUseCase
        .execute(
            new GetManagedBoardPostCommentsQuery(command.postId(), command.page(), command.size()))
        .map(
            comment ->
                new AdminBoardCommentView(
                    comment.commentId(),
                    comment.postId(),
                    comment.writerId(),
                    comment.content(),
                    comment.parentId(),
                    comment.isDeleted(),
                    comment.createdAt()));
  }
}
