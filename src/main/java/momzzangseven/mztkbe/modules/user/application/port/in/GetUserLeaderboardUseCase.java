package momzzangseven.mztkbe.modules.user.application.port.in;

import momzzangseven.mztkbe.modules.user.application.dto.GetUserLeaderboardResult;

/** Input port for retrieving the public user leaderboard. */
public interface GetUserLeaderboardUseCase {

  GetUserLeaderboardResult execute();
}
