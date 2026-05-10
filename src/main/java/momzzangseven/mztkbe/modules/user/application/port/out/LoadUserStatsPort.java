package momzzangseven.mztkbe.modules.user.application.port.out;

import java.util.Collection;
import java.util.List;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;

/** Output port for aggregated user statistics queries. */
public interface LoadUserStatsPort {

  UserStatusCounts loadUserStatusCounts();

  List<UserRoleCount> loadRoleCounts(Collection<UserRole> roles);

  record UserStatusCounts(long totalUserCount, long activeUserCount, long blockedUserCount) {}

  record UserRoleCount(UserRole role, long count) {}
}
