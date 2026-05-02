package momzzangseven.mztkbe.modules.admin.board.application.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardCommentResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostCommentsCommand;
import momzzangseven.mztkbe.modules.admin.board.application.port.in.GetAdminBoardPostCommentsUseCase;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardPostCommentsPort;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.LoadAdminBoardWriterNicknamesPort;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for admin board post comment reads. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetAdminBoardPostCommentsService implements GetAdminBoardPostCommentsUseCase {

  private final LoadAdminBoardPostCommentsPort loadAdminBoardPostCommentsPort;
  private final LoadAdminBoardWriterNicknamesPort loadAdminBoardWriterNicknamesPort;

  @Override
  @AdminOnly(
      actionType = "ADMIN_BOARD_POST_COMMENTS_VIEW",
      targetType = AuditTargetType.COMMENT,
      operatorId = "#command.operatorUserId",
      targetId = "#command.postId")
  public Page<AdminBoardCommentResult> execute(GetAdminBoardPostCommentsCommand command) {
    command.validate();
    Page<LoadAdminBoardPostCommentsPort.AdminBoardCommentView> comments =
        loadAdminBoardPostCommentsPort.load(command);
    Map<Long, String> writerNicknames =
        loadAdminBoardWriterNicknamesPort.load(
            comments.getContent().stream()
                .map(LoadAdminBoardPostCommentsPort.AdminBoardCommentView::writerId)
                .toList());
    return comments.map(comment -> toResult(comment, writerNicknames));
  }

  private AdminBoardCommentResult toResult(
      LoadAdminBoardPostCommentsPort.AdminBoardCommentView comment,
      Map<Long, String> writerNicknames) {
    return new AdminBoardCommentResult(
        comment.commentId(),
        comment.postId(),
        comment.writerId(),
        writerNicknames.get(comment.writerId()),
        comment.content(),
        comment.parentId(),
        comment.isDeleted(),
        comment.createdAt());
  }
}
