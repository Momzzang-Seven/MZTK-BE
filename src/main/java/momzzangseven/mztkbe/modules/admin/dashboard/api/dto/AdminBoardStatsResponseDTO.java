package momzzangseven.mztkbe.modules.admin.dashboard.api.dto;

import java.util.Map;
import momzzangseven.mztkbe.modules.admin.dashboard.application.dto.AdminBoardStatsResult;

/**
 * Response DTO for {@code GET /admin/dashboard/post-stats}.
 *
 * <p>{@code moderationActionReasonStats} is the preferred field for cumulative reason-code counts
 * from append-only {@code admin_board_moderation_actions} rows. It is not a current
 * deleted/blocked-post state count and can include both post and comment moderation actions.
 * Unblock and restore actions are intentionally excluded because they are not stored in that
 * statistics table.
 *
 * <p>{@code postRemovalReasonStats} is a legacy compatibility alias for the same value.
 *
 * @param postRemovalReasonStats legacy alias for {@code moderationActionReasonStats}; prefer {@code
 *     moderationActionReasonStats} for new clients
 * @param moderationActionReasonStats cumulative moderation action reason-code counts from {@code
 *     admin_board_moderation_actions}
 * @param boardTypeSplit cumulative moderation action counts by board type
 * @param targetTypeStats cumulative moderation action counts by target type
 */
public record AdminBoardStatsResponseDTO(
    Map<String, Long> postRemovalReasonStats,
    Map<String, Long> moderationActionReasonStats,
    Map<String, Long> boardTypeSplit,
    Map<String, Long> targetTypeStats) {

  public static AdminBoardStatsResponseDTO from(AdminBoardStatsResult result) {
    Map<String, Long> reasonStats = result.postRemovalReasonStats();
    return new AdminBoardStatsResponseDTO(
        reasonStats, reasonStats, result.boardTypeSplit(), result.targetTypeStats());
  }
}
