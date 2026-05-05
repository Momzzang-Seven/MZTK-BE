package momzzangseven.mztkbe.modules.admin.dashboard.api.dto;

import java.util.Map;
import momzzangseven.mztkbe.modules.admin.dashboard.application.dto.AdminUserStatsResult;

/** Response DTO for {@code GET /admin/dashboard/user-stats}. */
public record AdminUserStatsResponseDTO(
    long totalUserCount,
    long activeUserCount,
    long blockedUserCount,
    Map<String, Long> roleCounts) {

  public static AdminUserStatsResponseDTO from(AdminUserStatsResult result) {
    return new AdminUserStatsResponseDTO(
        result.totalUserCount(),
        result.activeUserCount(),
        result.blockedUserCount(),
        result.roleCounts());
  }
}
