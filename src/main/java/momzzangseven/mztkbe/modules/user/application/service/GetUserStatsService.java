package momzzangseven.mztkbe.modules.user.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.user.application.dto.GetUserStatsResult;
import momzzangseven.mztkbe.modules.user.application.port.in.GetUserStatsUseCase;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserStatsPort;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for aggregated user statistics used by admin read models. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetUserStatsService implements GetUserStatsUseCase {

  private static final List<UserRole> COUNTED_ROLES = List.of(UserRole.USER, UserRole.TRAINER);

  private final LoadUserStatsPort loadUserStatsPort;

  @Override
  public GetUserStatsResult execute() {
    LoadUserStatsPort.UserStatusCounts statusCounts = loadUserStatsPort.loadUserStatusCounts();
    Map<UserRole, Long> roleCounts = initializeRoleCounts();
    loadUserStatsPort
        .loadRoleCounts(COUNTED_ROLES)
        .forEach(roleCount -> roleCounts.put(roleCount.role(), roleCount.count()));

    return new GetUserStatsResult(
        statusCounts.totalUserCount(),
        statusCounts.activeUserCount(),
        statusCounts.blockedUserCount(),
        roleCounts);
  }

  private Map<UserRole, Long> initializeRoleCounts() {
    Map<UserRole, Long> roleCounts = new LinkedHashMap<>();
    COUNTED_ROLES.forEach(role -> roleCounts.put(role, 0L));
    return roleCounts;
  }
}
