package momzzangseven.mztkbe.modules.user.application.dto;

import java.util.List;

/** Result of {@code GetUserLeaderboardUseCase}. */
public record GetUserLeaderboardResult(List<LeaderboardUserItem> users) {

  public GetUserLeaderboardResult {
    users = List.copyOf(users);
  }
}
