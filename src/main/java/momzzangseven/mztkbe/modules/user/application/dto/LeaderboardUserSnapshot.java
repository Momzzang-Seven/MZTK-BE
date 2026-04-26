package momzzangseven.mztkbe.modules.user.application.dto;

/** Read-model row loaded from persistence for leaderboard ranking. */
public record LeaderboardUserSnapshot(
    Long userId,
    String nickname,
    String profileImageUrl,
    int level,
    int lifetimeXp,
    int availableXp) {}
