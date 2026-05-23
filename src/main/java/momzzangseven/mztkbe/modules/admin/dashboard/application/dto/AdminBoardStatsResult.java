package momzzangseven.mztkbe.modules.admin.dashboard.application.dto;

import java.util.Map;

/**
 * Result of {@code GetAdminBoardStatsUseCase}.
 *
 * <p>{@code postRemovalReasonStats} preserves the public response field name, but the value is
 * cumulative reason-code counts from {@code admin_board_moderation_actions}. It is not derived from
 * the current {@code posts.moderation_status} state and can include comment moderation actions.
 */
public record AdminBoardStatsResult(
    Map<String, Long> postRemovalReasonStats,
    Map<String, Long> boardTypeSplit,
    Map<String, Long> targetTypeStats) {

  public AdminBoardStatsResult {
    postRemovalReasonStats = Map.copyOf(postRemovalReasonStats);
    boardTypeSplit = Map.copyOf(boardTypeSplit);
    targetTypeStats = Map.copyOf(targetTypeStats);
  }
}
