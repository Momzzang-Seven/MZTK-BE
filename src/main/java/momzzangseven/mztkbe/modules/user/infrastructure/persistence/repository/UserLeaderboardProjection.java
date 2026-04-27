package momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository;

/** JPQL projection for leaderboard rows. */
public record UserLeaderboardProjection(
    Long userId,
    String nickname,
    String profileImageUrl,
    int level,
    int lifetimeXp,
    int availableXp) {}
