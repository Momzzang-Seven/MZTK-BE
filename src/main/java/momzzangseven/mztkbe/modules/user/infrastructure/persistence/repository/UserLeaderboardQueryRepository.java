package momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Query repository for leaderboard-specific read operations. */
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
