package momzzangseven.mztkbe.modules.admin.dashboard.infrastructure.external.user;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.dashboard.application.port.out.LoadAdminUserStatsPort;
import momzzangseven.mztkbe.modules.user.application.dto.GetUserStatsResult;
import momzzangseven.mztkbe.modules.user.application.port.in.GetUserStatsUseCase;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.stereotype.Component;

/** Cross-module adapter that exposes user statistics to the admin dashboard module. */
@Component
@RequiredArgsConstructor
public class AdminUserStatsAdapter implements LoadAdminUserStatsPort {

  private final GetUserStatsUseCase getUserStatsUseCase;

  @Override
  public AdminUserStatsView load() {
    GetUserStatsResult result = getUserStatsUseCase.execute();
    return new AdminUserStatsView(
        result.totalUserCount(),
        result.activeUserCount(),
        result.blockedUserCount(),
        toRoleCounts(result));
  }

  private Map<String, Long> toRoleCounts(GetUserStatsResult result) {
    Map<String, Long> roleCounts = new LinkedHashMap<>();
    roleCounts.put(UserRole.USER.name(), result.roleCounts().getOrDefault(UserRole.USER, 0L));
    roleCounts.put(UserRole.TRAINER.name(), result.roleCounts().getOrDefault(UserRole.TRAINER, 0L));
    return roleCounts;
  }
}
