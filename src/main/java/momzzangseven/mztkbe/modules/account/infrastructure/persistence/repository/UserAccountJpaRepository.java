package momzzangseven.mztkbe.modules.account.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.entity.UserAccountEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository for {@link UserAccountEntity}. */
public interface UserAccountJpaRepository extends JpaRepository<UserAccountEntity, Long> {

  Optional<UserAccountEntity> findByUserId(Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT ua FROM UserAccountEntity ua WHERE ua.userId = :userId")
  Optional<UserAccountEntity> findByUserIdForUpdate(@Param("userId") Long userId);

  Optional<UserAccountEntity> findByProviderAndProviderUserId(
      AuthProvider provider, String providerUserId);

  Optional<UserAccountEntity> findByProviderAndProviderUserIdAndStatus(
      AuthProvider provider, String providerUserId, AccountStatus status);

  /**
   * Returns account whose associated {@code users} row has the given email address and status.
   *
   * <p>Joins {@code users_account} with {@code users} because email is owned by the {@code users}
   * table.
   */
  @Query(
      "SELECT ua FROM UserAccountEntity ua "
          + "JOIN UserEntity u ON u.id = ua.userId "
          + "WHERE u.email = :email AND ua.status = :status")
  Optional<UserAccountEntity> findByEmailAndStatus(
      @Param("email") String email, @Param("status") AccountStatus status);

  /**
   * Returns the user IDs of accounts that are DELETED and whose {@code deleted_at} is before the
   * given cutoff. Used by the hard-delete batch job.
   */
  @Query(
      "SELECT ua.userId FROM UserAccountEntity ua "
          + "WHERE ua.status = :status AND ua.deletedAt < :cutoff "
          + "ORDER BY ua.deletedAt asc, ua.id asc")
  List<Long> findUserIdsByStatusAndDeletedAtBefore(
      @Param("status") AccountStatus status, @Param("cutoff") Instant cutoff, Pageable pageable);

  @Query(
      "SELECT ua.userId AS userId, ua.status AS status "
          + "FROM UserAccountEntity ua "
          + "WHERE ua.userId IN :userIds")
  List<ManagedUserAccountStatusProjection> findManagedUserAccountStatusesByUserIds(
      @Param("userIds") List<Long> userIds);

  @Query(
      "SELECT ua.userId AS userId, ua.status AS status "
          + "FROM UserAccountEntity ua "
          + "WHERE ua.status = :status")
  List<ManagedUserAccountStatusProjection> findManagedUserAccountStatusesByStatus(
      @Param("status") AccountStatus status);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM UserAccountEntity ua WHERE ua.userId = :userId")
  void deleteByUserId(@Param("userId") Long userId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM UserAccountEntity ua WHERE ua.userId IN :userIds")
  void deleteByUserIdIn(@Param("userIds") List<Long> userIds);

  interface ManagedUserAccountStatusProjection {
    Long getUserId();

    AccountStatus getStatus();
  }
}
