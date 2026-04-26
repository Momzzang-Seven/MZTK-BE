package momzzangseven.mztkbe.modules.user.application.port.out;

import java.util.Collection;
import java.util.List;
import momzzangseven.mztkbe.modules.user.application.dto.LeaderboardUserSnapshot;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;

/** Output port for loading leaderboard candidates from persistence. */
public interface LoadUserLeaderboardPort {

  List<LeaderboardUserSnapshot> loadTopLeaderboardUsers(Collection<UserRole> roles, int limit);
}
