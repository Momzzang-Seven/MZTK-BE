package momzzangseven.mztkbe.modules.admin.board.application.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardCommentSearchResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardCommentsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.GetAdminBoardCommentsUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardCommentsPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardWriterNicknamesPort;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for admin board global comment search reads. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetAdminBoardCommentsService implements GetAdminBoardCommentsUseCase {

  private final LoadAdminBoardCommentsPort loadAdminBoardCommentsPort;
  private final LoadAdminBoardWriterNicknamesPort loadAdminBoardWriterNicknamesPort;

  @Override
  @AdminOnly(
      actionType = "ADMIN_BOARD_COMMENTS_VIEW",
      targetType = AuditTargetType.COMMENT,
      operatorId = "#command.operatorUserId",
      targetId = "'comments'",
      audit = false)
  public Page<AdminBoardCommentSearchResult> execute(GetAdminBoardCommentsCommand command) {
    Page<LoadAdminBoardCommentsPort.AdminBoardCommentView> comments =
        loadAdminBoardCommentsPort.load(
            new LoadAdminBoardCommentsPort.AdminBoardCommentQuery(
                command.search(),
                command.commentId(),
                command.userId(),
                command.targetType(),
                command.page(),
                command.size(),
                command.sortKey()));
    Map<Long, String> nicknames =
        loadAdminBoardWriterNicknamesPort.load(
            comments.getContent().stream()
                .map(LoadAdminBoardCommentsPort.AdminBoardCommentView::userId)
                .toList());
    return comments.map(comment -> toResult(comment, nicknames));
  }

  private AdminBoardCommentSearchResult toResult(
      LoadAdminBoardCommentsPort.AdminBoardCommentView comment, Map<Long, String> nicknames) {
    return new AdminBoardCommentSearchResult(
        comment.commentId(),
        comment.postId(),
        comment.answerId(),
        comment.targetType(),
        comment.userId(),
        nicknames.get(comment.userId()),
        comment.content(),
        comment.isDeleted(),
        comment.createdAt(),
        comment.updatedAt());
  }
}
