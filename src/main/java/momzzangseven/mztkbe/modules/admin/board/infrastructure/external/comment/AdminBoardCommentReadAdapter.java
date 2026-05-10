package momzzangseven.mztkbe.modules.admin.board.infrastructure.external.comment;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardCommentsPort;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardCommentTargetType;
import momzzangseven.mztkbe.modules.comment.application.dto.GetManagedBoardCommentsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.ManagedBoardCommentTargetType;
import momzzangseven.mztkbe.modules.comment.application.port.in.GetManagedBoardCommentsUseCase;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminBoardCommentReadAdapter implements LoadAdminBoardCommentsPort {

  private final GetManagedBoardCommentsUseCase getManagedBoardCommentsUseCase;

  @Override
  public Page<AdminBoardCommentView> load(AdminBoardCommentQuery query) {
    return getManagedBoardCommentsUseCase
        .execute(
            new GetManagedBoardCommentsQuery(
                query.search(),
                query.commentId(),
                query.userId(),
                toCommentTargetType(query.targetType()),
                query.page(),
                query.size(),
                query.sortKey().name()))
        .map(
            comment ->
                new AdminBoardCommentView(
                    comment.commentId(),
                    comment.postId(),
                    comment.answerId(),
                    toAdminBoardCommentTargetType(comment.targetType()),
                    comment.writerId(),
                    comment.content(),
                    comment.isDeleted(),
                    comment.createdAt(),
                    comment.updatedAt()));
  }

  private ManagedBoardCommentTargetType toCommentTargetType(
      AdminBoardCommentTargetType targetType) {
    return targetType == null ? null : ManagedBoardCommentTargetType.valueOf(targetType.name());
  }

  private AdminBoardCommentTargetType toAdminBoardCommentTargetType(
      ManagedBoardCommentTargetType targetType) {
    return AdminBoardCommentTargetType.valueOf(targetType.name());
  }
}
