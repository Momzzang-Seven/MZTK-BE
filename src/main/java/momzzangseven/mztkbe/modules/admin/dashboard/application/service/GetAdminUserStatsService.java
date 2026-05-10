package momzzangseven.mztkbe.modules.admin.dashboard.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.admin.dashboard.application.dto.AdminUserStatsResult;
import momzzangseven.mztkbe.modules.admin.dashboard.application.port.in.GetAdminUserStatsUseCase;
import momzzangseven.mztkbe.modules.admin.dashboard.application.port.out.LoadAdminUserStatsPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for admin dashboard user statistics. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetAdminUserStatsService implements GetAdminUserStatsUseCase {

  private final LoadAdminUserStatsPort loadAdminUserStatsPort;

  @Override
  @AdminOnly(
      actionType = "DASHBOARD_USER_STATS_VIEW",
      targetType = AuditTargetType.DASHBOARD,
      operatorId = "#operatorUserId",
      targetId = "'user-stats'")
  public AdminUserStatsResult execute(Long operatorUserId) {
    LoadAdminUserStatsPort.AdminUserStatsView view = loadAdminUserStatsPort.load();
    return new AdminUserStatsResult(
        view.totalUserCount(), view.activeUserCount(), view.blockedUserCount(), view.roleCounts());
  }
}
