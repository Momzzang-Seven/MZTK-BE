package momzzangseven.mztkbe.modules.admin.board.application.port.in;

import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardModerationResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.BanAdminBoardPostCommand;

/** Input port for admin post ban requests. */
public interface BanAdminBoardPostUseCase {

  AdminBoardModerationResult execute(BanAdminBoardPostCommand command);
}
