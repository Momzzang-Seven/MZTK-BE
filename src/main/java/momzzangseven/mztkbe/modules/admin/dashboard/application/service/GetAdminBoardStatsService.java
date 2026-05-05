package momzzangseven.mztkbe.modules.admin.dashboard.application.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardType;
import momzzangseven.mztkbe.modules.admin.dashboard.application.dto.AdminBoardStatsResult;
import momzzangseven.mztkbe.modules.admin.dashboard.application.port.in.GetAdminBoardStatsUseCase;
import momzzangseven.mztkbe.modules.admin.dashboard.application.port.out.LoadAdminBoardStatsPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for admin dashboard board moderation statistics. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetAdminBoardStatsService implements GetAdminBoardStatsUseCase {

  private final LoadAdminBoardStatsPort loadAdminBoardStatsPort;

  @Override
  @AdminOnly(
      actionType = "DASHBOARD_POST_STATS_VIEW",
      targetType = AuditTargetType.DASHBOARD,
      operatorId = "#operatorUserId",
      targetId = "'post-stats'")
  public AdminBoardStatsResult execute(Long operatorUserId) {
    LoadAdminBoardStatsPort.AdminBoardStatsView view = loadAdminBoardStatsPort.load();
    return new AdminBoardStatsResult(
        toOrderedStringMap(AdminBoardModerationReasonCode.values(), view.reasonCodeCounts()),
        toOrderedStringMap(AdminBoardType.values(), view.boardTypeCounts()),
        toOrderedStringMap(AdminBoardModerationTargetType.values(), view.targetTypeCounts()));
  }

  private static <E extends Enum<E>> Map<String, Long> toOrderedStringMap(
      E[] values, Map<E, Long> counts) {
    Map<String, Long> result = new LinkedHashMap<>();
    Arrays.stream(values)
        .forEach(value -> result.put(value.name(), counts.getOrDefault(value, 0L)));
    return result;
  }
}
