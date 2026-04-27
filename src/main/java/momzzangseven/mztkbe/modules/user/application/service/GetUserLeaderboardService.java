package momzzangseven.mztkbe.modules.user.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.user.application.dto.GetUserLeaderboardResult;
import momzzangseven.mztkbe.modules.user.application.dto.LeaderboardUserItem;
import momzzangseven.mztkbe.modules.user.application.dto.LeaderboardUserSnapshot;
import momzzangseven.mztkbe.modules.user.application.port.in.GetUserLeaderboardUseCase;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserLeaderboardPort;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for the public user leaderboard. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GetUserLeaderboardService implements GetUserLeaderboardUseCase {

  static final int LEADERBOARD_LIMIT = 10;
  private static final List<UserRole> LEADERBOARD_ROLES = List.of(UserRole.USER, UserRole.TRAINER);

  private final LoadUserLeaderboardPort loadUserLeaderboardPort;

  @Override
  public GetUserLeaderboardResult execute() {
    List<LeaderboardUserItem> users =
        loadUserLeaderboardPort
            .loadTopLeaderboardUsers(LEADERBOARD_ROLES, LEADERBOARD_LIMIT)
            .stream()
            .map(this::toItemWithoutRank)
            .toList();

    return new GetUserLeaderboardResult(applyRanks(users));
  }

  private LeaderboardUserItem toItemWithoutRank(LeaderboardUserSnapshot snapshot) {
    return new LeaderboardUserItem(
        0,
        snapshot.userId(),
        snapshot.nickname(),
        snapshot.profileImageUrl(),
        snapshot.level(),
        snapshot.lifetimeXp());
  }

  private List<LeaderboardUserItem> applyRanks(List<LeaderboardUserItem> users) {
    return java.util.stream.IntStream.range(0, users.size())
        .mapToObj(
            index -> {
              LeaderboardUserItem user = users.get(index);
              return new LeaderboardUserItem(
                  index + 1,
                  user.userId(),
                  user.nickname(),
                  user.profileImageUrl(),
                  user.level(),
                  user.lifetimeXp());
            })
        .toList();
  }
}
