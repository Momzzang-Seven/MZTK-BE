package momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Query repository for leaderboard-specific read operations.
 *
 * <p>Architecture note: this repository intentionally performs a single read-only query across the
 * {@code users}, {@code users_account}, and {@code user_progress} schemas, even though that spans
 * persistence concerns that may later be separated by module boundaries.
 *
 * <p>This exception is currently accepted because the leaderboard requires exact top-N ordering and
 * filtering in one query: only ACTIVE accounts, admin exclusion via allowed roles, inclusion of
 * users without progress rows via {@code coalesce}, and deterministic ordering by {@code
 * level/lifetimeXp/availableXp/userId} without N+1 queries.
 *
 * <p>If this read model grows or the surrounding modules are split further, prefer extracting the
 * leaderboard query into a dedicated read model or module instead of expanding this exception.
 */
public interface UserLeaderboardQueryRepository extends Repository<UserEntity, Long> {

  @Query(
      """
      select new momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserLeaderboardProjection(
          u.id,
          u.nickname,
          u.profileImageUrl,
          coalesce(up.level, 1),
          coalesce(up.lifetimeXp, 0),
          coalesce(up.availableXp, 0)
      )
      from UserEntity u
      join UserAccountEntity ua on ua.userId = u.id
      left join UserProgressEntity up on up.userId = u.id
      where ua.status = momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus.ACTIVE
        and u.role in :roles
      order by coalesce(up.level, 1) desc,
               coalesce(up.lifetimeXp, 0) desc,
               coalesce(up.availableXp, 0) desc,
               u.id asc
      """)
  List<UserLeaderboardProjection> findTopLeaderboardUsers(
      @Param("roles") Collection<UserRole> roles, Pageable pageable);
}
