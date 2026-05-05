package momzzangseven.mztkbe.modules.admin.dashboard.application.dto;

import java.util.Map;

/** Result of {@code GetAdminUserStatsUseCase}. */
public record AdminUserStatsResult(
    long totalUserCount,
    long activeUserCount,
    long blockedUserCount,
    Map<String, Long> roleCounts) {

  public AdminUserStatsResult {
    roleCounts = Map.copyOf(roleCounts);
  }
}
