package momzzangseven.mztkbe.modules.user.application.dto;

/** Single leaderboard row returned by {@code GetUserLeaderboardUseCase}. */
public record LeaderboardUserItem(
    int rank, Long userId, String nickname, String profileImageUrl, int level, int lifetimeXp) {}
