package momzzangseven.mztkbe.modules.admin.dashboard.application.port.in;

import momzzangseven.mztkbe.modules.admin.dashboard.application.dto.AdminUserStatsResult;

/** Inbound use case for the admin dashboard user statistics card. */
public interface GetAdminUserStatsUseCase {

  AdminUserStatsResult execute(Long operatorUserId);
}
