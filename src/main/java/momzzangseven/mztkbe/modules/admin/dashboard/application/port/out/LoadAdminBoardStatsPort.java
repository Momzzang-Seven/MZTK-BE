package momzzangseven.mztkbe.modules.admin.dashboard.application.port.out;

import java.util.Map;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardType;

/**
 * Output port for loading cumulative board moderation action statistics into the admin dashboard.
 *
 * <p>Implementations must load from the append-only {@code admin_board_moderation_actions} read
 * model, not from current post/comment visibility or moderation state.
 */
public interface LoadAdminBoardStatsPort {

  AdminBoardStatsView load();

  record AdminBoardStatsView(
      Map<AdminBoardModerationReasonCode, Long> reasonCodeCounts,
      Map<AdminBoardType, Long> boardTypeCounts,
      Map<AdminBoardModerationTargetType, Long> targetTypeCounts) {

    public AdminBoardStatsView {
      reasonCodeCounts = Map.copyOf(reasonCodeCounts);
      boardTypeCounts = Map.copyOf(boardTypeCounts);
      targetTypeCounts = Map.copyOf(targetTypeCounts);
    }
  }
}
