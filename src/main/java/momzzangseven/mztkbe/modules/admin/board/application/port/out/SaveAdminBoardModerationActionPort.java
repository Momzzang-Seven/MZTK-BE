package momzzangseven.mztkbe.modules.admin.board.application.port.out;

import momzzangseven.mztkbe.modules.admin.board.domain.model.AdminBoardModerationAction;

public interface SaveAdminBoardModerationActionPort {

  AdminBoardModerationAction save(AdminBoardModerationAction action);
}
