package momzzangseven.mztkbe.modules.user.api.dto;

import java.util.List;
import momzzangseven.mztkbe.modules.user.application.dto.GetUserLeaderboardResult;

/** HTTP response DTO for {@code GET /users/leaderboard}. */
public record GetUserLeaderboardResponseDTO(List<LeaderboardUserResponseDTO> users) {

  public static GetUserLeaderboardResponseDTO from(GetUserLeaderboardResult result) {
    return new GetUserLeaderboardResponseDTO(
        result.users().stream().map(LeaderboardUserResponseDTO::from).toList());
  }
}
