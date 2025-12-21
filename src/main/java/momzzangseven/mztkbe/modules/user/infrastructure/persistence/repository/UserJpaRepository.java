package momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository;

import java.util.Optional;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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

  /** Find user by wallet address. */
  Optional<UserEntity> findByWalletAddress(String walletAddress);

  /** Check if email exists. */
  boolean existsByEmail(String email);
}
