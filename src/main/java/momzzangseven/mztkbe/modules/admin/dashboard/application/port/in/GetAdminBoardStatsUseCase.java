package momzzangseven.mztkbe.modules.admin.dashboard.application.port.in;

import momzzangseven.mztkbe.modules.admin.dashboard.application.dto.AdminBoardStatsResult;

/** Input port for loading admin dashboard board moderation statistics. */
public interface GetAdminBoardStatsUseCase {

  AdminBoardStatsResult execute(Long operatorUserId);
}
