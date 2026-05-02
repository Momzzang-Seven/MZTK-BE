package momzzangseven.mztkbe.modules.admin.dashboard.application.port.out;

import java.util.Map;

/** Output port for loading user statistics into the admin dashboard. */
public interface LoadAdminUserStatsPort {

  AdminUserStatsView load();

  record AdminUserStatsView(
      long totalUserCount,
      long activeUserCount,
      long blockedUserCount,
      Map<String, Long> roleCounts) {

    public AdminUserStatsView {
      roleCounts = Map.copyOf(roleCounts);
    }
  }
}
