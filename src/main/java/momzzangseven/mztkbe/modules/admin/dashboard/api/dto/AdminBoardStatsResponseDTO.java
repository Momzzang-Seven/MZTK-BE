package momzzangseven.mztkbe.modules.admin.dashboard.api.dto;

import java.util.Map;
import momzzangseven.mztkbe.modules.admin.dashboard.application.dto.AdminBoardStatsResult;

/**
 * Response DTO for {@code GET /admin/dashboard/post-stats}.
 *
 * <p>{@code postRemovalReasonStats} is a legacy API field name kept for compatibility. It is not a
 * current deleted/blocked-post state count. It contains cumulative reason-code counts from
 * append-only {@code admin_board_moderation_actions} rows, including both post and comment
 * moderation actions. Unblock actions are intentionally excluded because they are not stored in
 * that statistics table.
 */
public record AdminBoardStatsResponseDTO(
    Map<String, Long> postRemovalReasonStats,
    Map<String, Long> boardTypeSplit,
    Map<String, Long> targetTypeStats) {

  public static AdminBoardStatsResponseDTO from(AdminBoardStatsResult result) {
    return new AdminBoardStatsResponseDTO(
        result.postRemovalReasonStats(), result.boardTypeSplit(), result.targetTypeStats());
  }
}
