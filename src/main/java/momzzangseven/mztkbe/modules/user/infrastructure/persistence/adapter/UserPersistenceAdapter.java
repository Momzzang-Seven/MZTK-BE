package momzzangseven.mztkbe.modules.user.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter implementing LoadUserPort and SaveUserPort.
 *
 * <p>Hexagonal Architecture: - This is an ADAPTER in the infrastructure layer - Implements OUTPUT
 * PORTS defined by the application layer - Translates between Domain Model (User) and
 * Infrastructure Model (UserEntity)
 *
 * <p>Responsibilities: - Execute database operations via UserJpaRepository - Convert UserEntity ↔
 * User (Domain Model) - Handle transaction boundaries
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements LoadUserPort, SaveUserPort {

  private final UserJpaRepository userJpaRepository;

  // ========== LoadUserPort Implementation ==========

  @Override
  @Transactional(readOnly = true)
  public Optional<User> loadUserByEmail(String email) {
    log.debug("Loading user by email: {}", email);
    return userJpaRepository.findByEmail(email).map(this::mapToDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<User> loadUserByKakaoId(String kakaoId) {
    log.debug("Loading user by Kakao ID: {}", kakaoId);
    return userJpaRepository
        .findByProviderAndProviderUserId(AuthProvider.KAKAO, kakaoId)
        .map(this::mapToDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<User> loadUserByGoogleId(String googleId) {
    log.debug("Loading user by Google ID: {}", googleId);
    return userJpaRepository
        .findByProviderAndProviderUserId(AuthProvider.GOOGLE, googleId)
        .map(this::mapToDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<User> loadUserByWalletAddress(String walletAddress) {
    log.debug("Loading user by wallet address: {}", walletAddress);
    return userJpaRepository.findByWalletAddress(walletAddress).map(this::mapToDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<User> loadUserById(Long id) {
    log.debug("Loading user by ID: {}", id);
    return userJpaRepository.findById(id).map(this::mapToDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByEmail(String email) {
    log.debug("Checking if email exists: {}", email);
    return userJpaRepository.existsByEmail(email);
  }

  // ========== SaveUserPort Implementation ==========

  @Override
  @Transactional
  public User saveUser(User user) {
    log.debug("Saving user: {}", user.getEmail());

    UserEntity entity;

    if (user.getId() != null) {
      // Update existing user
      entity =
          userJpaRepository
              .findById(user.getId())
              .orElseThrow(
                  () -> new IllegalArgumentException("User not found with ID: " + user.getId()));

      // Update mutable fields
      entity = updateEntityFromDomain(entity, user);
    } else {
      // Create new user
      entity = mapToEntity(user);
    }

    UserEntity savedEntity = userJpaRepository.save(entity);
    log.debug("User saved with ID: {}", savedEntity.getId());

    return mapToDomain(savedEntity);
  }

  @Override
  @Transactional
  public void deleteUser(Long userId) {
    log.debug("Deleting user with ID: {}", userId);
    userJpaRepository.deleteById(userId);
  }

  // ========== Mapping Methods (Translator Pattern) ==========

  /** Convert UserEntity (Infrastructure) to User (Domain). */
  private User mapToDomain(UserEntity entity) {
    return User.builder()
        .id(entity.getId())
        .email(entity.getEmail())
        .password(entity.getPasswordHash())
        .nickname(entity.getNickname())
        .profileImageUrl(entity.getProfileImageUrl())
        .provider_user_id(entity.getProviderUserId())
        .walletAddress(entity.getWalletAddress())
        .authProvider(entity.getProvider())
        .role(entity.getRole())
        .lastLoginAt(entity.getLastLoginAt())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  /** Convert User (Domain) to UserEntity (Infrastructure). Used for creating new entities. */
  private UserEntity mapToEntity(User user) {
    return UserEntity.builder()
        .id(user.getId())
        .email(user.getEmail())
        .passwordHash(user.getPassword())
        .nickname(user.getNickname())
        .profileImageUrl(user.getProfileImageUrl())
        .providerUserId(user.getProvider_user_id())
        .walletAddress(user.getWalletAddress())
        .provider(user.getAuthProvider())
        .role(user.getRole())
        .lastLoginAt(user.getLastLoginAt())
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .build();
  }

  /**
   * Update existing UserEntity from User (Domain). Used for updating entities to preserve JPA
   * managed state.
   */
  private UserEntity updateEntityFromDomain(UserEntity entity, User user) {
    return UserEntity.builder()
        .id(entity.getId()) // Preserve existing ID
        .email(user.getEmail())
        .passwordHash(user.getPassword())
        .nickname(user.getNickname())
        .profileImageUrl(user.getProfileImageUrl())
        .providerUserId(user.getProvider_user_id())
        .walletAddress(user.getWalletAddress())
        .provider(user.getAuthProvider())
        .role(user.getRole())
        .lastLoginAt(user.getLastLoginAt())
        .createdAt(entity.getCreatedAt()) // Preserve original creation time
        .updatedAt(user.getUpdatedAt())
        .build();
  }
}
