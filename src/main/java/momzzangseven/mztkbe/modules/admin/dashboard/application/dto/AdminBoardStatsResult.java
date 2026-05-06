package momzzangseven.mztkbe.modules.admin.dashboard.application.dto;

import java.util.Map;

/** Result of {@code GetAdminBoardStatsUseCase}. */
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
