package momzzangseven.mztkbe.modules.user.api.dto;

import momzzangseven.mztkbe.modules.user.application.dto.LeaderboardUserItem;

/** HTTP response row for the public leaderboard endpoint. */
public record LeaderboardUserResponseDTO(
    int rank, Long userId, String nickname, String profileImageUrl, int level, int lifetimeXp) {

  public static LeaderboardUserResponseDTO from(LeaderboardUserItem item) {
    return new LeaderboardUserResponseDTO(
        item.rank(),
        item.userId(),
        item.nickname(),
        item.profileImageUrl(),
        item.level(),
        item.lifetimeXp());
  }
}
