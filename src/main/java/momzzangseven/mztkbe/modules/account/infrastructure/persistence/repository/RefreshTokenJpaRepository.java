package momzzangseven.mztkbe.modules.account.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for RefreshTokenEntity.
 *
 * <p>Infrastructure Layer: - Provides database access methods - Spring Data JPA automatically
 * implements these methods - Used by RefreshTokenPersistenceAdapter (not directly by application
 * layer)
 *
 * <p>Naming Convention: - findBy...: Query methods - deleteBy...: Delete methods - existsBy...:
 * Boolean check methods
 */
@Repository
public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, Long> {

  /**
   * Find refresh token by token hash (unique).
   *
   * @param tokenHash hashed JWT token string
   * @return Optional of RefreshTokenEntity
   */
  Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

  /** Find and lock token for update (PESSIMISTIC_WRITE). Prevents concurrent modifications. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT rt FROM RefreshTokenEntity rt WHERE rt.tokenHash = :tokenHash")
  Optional<RefreshTokenEntity> findByTokenHashWithLock(@Param("tokenHash") String tokenHash);

  /**
   * Find refresh token by user ID.
   *
   * <p>Note: Returns the most recent token if multiple exist. Use findAllByUserId() if you need all
   * tokens.
   *
   * @param userId User's unique identifier
   * @return Optional of RefreshTokenEntity
   */
  Optional<RefreshTokenEntity> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

  /**
   * Find all refresh tokens for a user.
   *
   * <p>Used for: - Logout (revoke all tokens) - Security: Detect multiple active sessions
   *
   * @param userId User's unique identifier
   * @return List of RefreshTokenEntity
   */
  List<RefreshTokenEntity> findAllByUserId(Long userId);

  /**
   * Find all non-revoked tokens for a user.
   *
   * @param userId User's unique identifier
   * @return List of active tokens
   */
  @Query(
      "SELECT rt FROM RefreshTokenEntity rt "
          + "WHERE rt.userId = :userId "
          + "AND rt.revokedAt is NULL "
          + "AND rt.expiresAt > :now")
  List<RefreshTokenEntity> findAllActiveByUserId(
      @Param("userId") Long userId, @Param("now") Instant now);

  /**
   * Check if token exists by token hash.
   *
   * @param tokenHash Hashed JWT token string
   * @return true if exists, false otherwise
   */
  boolean existsByTokenHash(String tokenHash);

  /**
   * Delete all tokens for a user.
   *
   * <p>Used for logout - remove all tokens at once.
   *
   * @param userId User's unique identifier
   */
  @Modifying
  @Query("DELETE FROM RefreshTokenEntity rt WHERE rt.userId = :userId")
  void deleteByUserId(@Param("userId") Long userId);

  @Modifying
  @Query("DELETE FROM RefreshTokenEntity rt WHERE rt.userId in :userIds")
  int deleteByUserIdIn(@Param("userIds") List<Long> userIds);

  /**
   * Delete expired tokens (cleanup job).
   *
   * <p>Should be called periodically to clean up old tokens.
   *
   * @param now Current timestamp
   * @return Number of deleted tokens
   */
  @Modifying
  @Query("DELETE FROM RefreshTokenEntity rt WHERE rt.expiresAt < :now")
  int deleteExpiredTokens(@Param("now") Instant now);

  /**
   * Count active tokens for a user.
   *
   * <p>Used for monitoring and rate limiting.
   *
   * @param userId User's unique identifier
   * @param now Current timestamp
   * @return Count of active tokens
   */
  @Query(
      "SELECT COUNT(rt) FROM RefreshTokenEntity rt "
          + "WHERE rt.userId = :userId "
          + "AND rt.revokedAt is NULL "
          + "AND rt.expiresAt > :now")
  long countActiveTokensByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
