package momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Query repository for admin-oriented user statistics.
 *
 * <p>Architecture note: like the leaderboard read model, this repository performs a read-only
 * cross-table query across {@code users} and {@code users_account} because the admin dashboard
 * needs account-status SSOT and role aggregation in one persistence slice without leaking
 * repositories into the admin application layer.
 */
public interface UserStatsQueryRepository extends Repository<UserEntity, Long> {

  @Query(
      """
      select count(ua.id)
      from UserAccountEntity ua
      join UserEntity u on u.id = ua.userId
      where u.role in :roles
      """)
  long countUserAccountsByRoles(@Param("roles") Collection<UserRole> roles);

  @Query(
      """
      select count(ua.id)
      from UserAccountEntity ua
      join UserEntity u on u.id = ua.userId
      where ua.status = :status
        and u.role in :roles
      """)
  long countUserAccountsByStatusAndRoles(
      @Param("status") AccountStatus status, @Param("roles") Collection<UserRole> roles);

  @Query(
      """
      select new momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserStatsProjection(
          u.role,
          count(u.id)
      )
      from UserEntity u
      join UserAccountEntity ua on ua.userId = u.id
      where u.role in :roles
      group by u.role
      """)
  List<UserStatsProjection> countUsersByRoles(@Param("roles") Collection<UserRole> roles);
}
