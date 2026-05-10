package momzzangseven.mztkbe.modules.admin.board.application.port.in;

import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardModerationResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.UnblockAdminBoardPostCommand;

/** Input port for admin post unblock requests. */
public interface UnblockAdminBoardPostUseCase {

  AdminBoardModerationResult execute(UnblockAdminBoardPostCommand command);
}
