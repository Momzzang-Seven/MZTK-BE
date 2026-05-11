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
                    comment.parentId(),
                    toAdminBoardCommentTargetType(comment.targetType()),
                    comment.writerId(),
                    comment.content(),
                    comment.isDeleted(),
                    comment.createdAt(),
                    comment.updatedAt()));
  }

  private ManagedBoardCommentTargetType toCommentTargetType(
      AdminBoardCommentTargetType targetType) {
    if (targetType == null) {
      return null;
    }
    return switch (targetType) {
      case POST -> ManagedBoardCommentTargetType.POST;
      case ANSWER -> ManagedBoardCommentTargetType.ANSWER;
    };
  }

  private AdminBoardCommentTargetType toAdminBoardCommentTargetType(
      ManagedBoardCommentTargetType targetType) {
    return switch (targetType) {
      case POST -> AdminBoardCommentTargetType.POST;
      case ANSWER -> AdminBoardCommentTargetType.ANSWER;
    };
  }
}
