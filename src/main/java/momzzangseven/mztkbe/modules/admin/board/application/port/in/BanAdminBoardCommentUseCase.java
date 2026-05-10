package momzzangseven.mztkbe.modules.admin.board.application.port.in;

import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardModerationResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.BanAdminBoardCommentCommand;

/** Input port for admin comment ban requests. */
public interface BanAdminBoardCommentUseCase {

  AdminBoardModerationResult execute(BanAdminBoardCommentCommand command);
}
