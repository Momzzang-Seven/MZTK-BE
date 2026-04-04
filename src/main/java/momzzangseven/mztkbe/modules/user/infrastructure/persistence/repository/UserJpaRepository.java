package momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for UserEntity.
 *
 * <p>Infrastructure Layer: - Provides database access methods - Spring Data JPA automatically
 * implements these methods - Used by UserPersistenceAdapter (not directly by application layer)
 */
@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

  /** Find user by email address. */
  Optional<UserEntity> findByEmail(String email);

  /**
   * Find user by provider and provider-specific user ID. This replaces separate findByKakaoId and
   * findByGoogleId methods.
   */
  Optional<UserEntity> findByProviderAndProviderUserId(
      AuthProvider provider, String providerUserId);

  /** Check if email exists. */
  boolean existsByEmail(String email);

  /**
   * Fetch user IDs by deletion cutoff.
   *
   * <p>Note: uses pagination to avoid loading too many rows at once.
   */
  @Query(
      "select u.id from UserEntity u "
          + "where u.status = :status and u.deletedAt is not null and u.deletedAt < :cutoff "
          + "order by u.deletedAt asc, u.id asc")
  List<Long> findIdsByStatusAndDeletedAtBefore(
      @Param("status") UserStatus status, @Param("cutoff") LocalDateTime cutoff, Pageable pageable);
}
