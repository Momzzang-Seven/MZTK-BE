package momzzangseven.mztkbe.modules.admin.dashboard.api.dto;

import java.util.Map;
import momzzangseven.mztkbe.modules.admin.dashboard.application.dto.AdminBoardStatsResult;

/** Response DTO for {@code GET /admin/dashboard/post-stats}. */
public record AdminBoardStatsResponseDTO(
    Map<String, Long> postRemovalReasonStats,
    Map<String, Long> boardTypeSplit,
    Map<String, Long> targetTypeStats) {

  public static AdminBoardStatsResponseDTO from(AdminBoardStatsResult result) {
    return new AdminBoardStatsResponseDTO(
        result.postRemovalReasonStats(), result.boardTypeSplit(), result.targetTypeStats());
  }
}
