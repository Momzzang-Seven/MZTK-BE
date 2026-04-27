package momzzangseven.mztkbe.modules.user.infrastructure.persistence.adapter;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.user.application.dto.LeaderboardUserSnapshot;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserLeaderboardPort;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserLeaderboardProjection;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserLeaderboardQueryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Persistence adapter for loading leaderboard rows in one query. */
@Component
@RequiredArgsConstructor
public class UserLeaderboardPersistenceAdapter implements LoadUserLeaderboardPort {

  private final UserLeaderboardQueryRepository userLeaderboardQueryRepository;

  @Override
  @Transactional(readOnly = true)
  public List<LeaderboardUserSnapshot> loadTopLeaderboardUsers(
      Collection<UserRole> roles, int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be > 0");
    }

    return userLeaderboardQueryRepository
        .findTopLeaderboardUsers(roles, PageRequest.of(0, limit))
        .stream()
        .map(this::toSnapshot)
        .toList();
  }

  private LeaderboardUserSnapshot toSnapshot(UserLeaderboardProjection projection) {
    return new LeaderboardUserSnapshot(
        projection.userId(),
        projection.nickname(),
        projection.profileImageUrl(),
        projection.level(),
        projection.lifetimeXp(),
        projection.availableXp());
  }
}
