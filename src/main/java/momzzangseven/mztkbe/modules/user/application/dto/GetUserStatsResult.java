package momzzangseven.mztkbe.modules.user.application.dto;

import java.util.Map;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;

/** Result of {@code GetUserStatsUseCase}. */
public record GetUserStatsResult(
    long totalUserCount,
    long activeUserCount,
    long blockedUserCount,
    Map<UserRole, Long> roleCounts) {

  public GetUserStatsResult {
    roleCounts = Map.copyOf(roleCounts);
  }
}
