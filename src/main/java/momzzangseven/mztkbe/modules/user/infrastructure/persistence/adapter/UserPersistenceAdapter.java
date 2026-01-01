package momzzangseven.mztkbe.modules.user.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.UserHardDeletePort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.UserEntity;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Persistence adapter implementing user port interfaces using UserJpaRepository. */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter
    implements LoadUserPort, SaveUserPort, UserHardDeletePort {

  private final UserJpaRepository userJpaRepository;

  // ========== LoadUserPort Implementation ==========

  @Override
  @Transactional(readOnly = true)
  public Optional<User> loadUserByEmail(String email) {
    log.debug("Loading user by email: {}", email);
    return userJpaRepository.findByEmail(email).filter(this::isActiveUser).map(this::mapToDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<User> loadDeletedUserByEmail(String email) {
    log.debug("Loading deleted user by email: {}", email);
    return userJpaRepository.findByEmail(email).filter(this::isDeletedUser).map(this::mapToDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<User> loadDeletedUserById(Long userId) {
    log.debug("Loading deleted user by id: {}", userId);
    return userJpaRepository.findById(userId).filter(this::isDeletedUser).map(this::mapToDomain);
  }

  /** LoadUserPort가 요구하는 메서드 (컴파일 에러 해결용) - provider + providerUserId 조합으로 유저 조회. */
  @Override
  @Transactional(readOnly = true)
  public Optional<User> findByProviderAndProviderUserId(
      AuthProvider provider, String providerUserId) {
    log.debug("Loading user by provider: {}, providerUserId: {}", provider, providerUserId);
    return userJpaRepository
        .findByProviderAndProviderUserId(provider, providerUserId)
        .filter(this::isActiveUser)
        .map(this::mapToDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<User> findDeletedByProviderAndProviderUserId(
      AuthProvider provider, String providerUserId) {
    log.debug("Loading deleted user by provider: {}, providerUserId: {}", provider, providerUserId);
    return userJpaRepository
        .findByProviderAndProviderUserId(provider, providerUserId)
        .filter(this::isDeletedUser)
        .map(this::mapToDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<User> loadUserById(Long userId) {
    log.debug("Loading user by id: {}", userId);
    return userJpaRepository.findById(userId).filter(this::isActiveUser).map(this::mapToDomain);
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
      // Update existing user (영속 상태 유지)
      entity =
          userJpaRepository
              .findById(user.getId())
              .orElseThrow(
                  () -> new IllegalArgumentException("User not found with ID: " + user.getId()));

      // Update by setting values on the existing entity (creating a new instance via builder may
      // break JPA managed state)
      updateEntityFromDomain(entity, user);
    } else {
      // Create new user
      entity = mapToEntity(user);
    }

    UserEntity savedEntity = userJpaRepository.save(entity);
    log.debug("User saved with ID: {}", savedEntity.getId());

    return mapToDomain(savedEntity);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Long> loadUserIdsForHardDelete(UserStatus status, LocalDateTime cutoff, int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be > 0");
    }
    return userJpaRepository.findIdsByStatusAndDeletedAtBefore(
        status, cutoff, PageRequest.of(0, limit));
  }

  @Override
  @Transactional
  public void deleteAllByIdInBatch(List<Long> userIds) {
    userJpaRepository.deleteAllByIdInBatch(userIds);
  }

  // ========== Mapping Methods (Translator Pattern) ==========

  private boolean isActiveUser(UserEntity entity) {
    return resolveStatus(entity) == UserStatus.ACTIVE;
  }

  private boolean isDeletedUser(UserEntity entity) {
    return resolveStatus(entity) == UserStatus.DELETED;
  }

  /** Convert UserEntity (Infrastructure) to User (Domain). */
  private User mapToDomain(UserEntity entity) {
    UserStatus status = resolveStatus(entity);

    // Invariant: ACTIVE users must always have deletedAt = null.
    // (If legacy/buggy data has deletedAt set for ACTIVE users, treat it as null and let the next
    // save operation persist the normalization.)
    LocalDateTime deletedAt = status == UserStatus.ACTIVE ? null : entity.getDeletedAt();
    return User.builder()
        .id(entity.getId())
        .email(entity.getEmail())
        .password(entity.getPasswordHash())
        .nickname(entity.getNickname())
        .profileImageUrl(entity.getProfileImageUrl())
        .providerUserId(entity.getProviderUserId())
        .googleRefreshToken(entity.getGoogleRefreshToken())
        .walletAddress(entity.getWalletAddress())
        .authProvider(entity.getProvider())
        .role(entity.getRole())
        .status(status)
        .lastLoginAt(entity.getLastLoginAt())
        .deletedAt(deletedAt)
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  /** Convert User (Domain) to UserEntity (Infrastructure). Used for creating new entities. */
  private UserEntity mapToEntity(User user) {
    String providerUserId = user.getProviderUserId();
    UserStatus status = user.getStatus() != null ? user.getStatus() : UserStatus.ACTIVE;

    // LOCAL은 providerUserId가 없으니 강제로 만들어 넣기
    if (providerUserId == null || providerUserId.isBlank()) {
      if (user.getAuthProvider() == AuthProvider.LOCAL) {
        providerUserId = "LOCAL:" + user.getEmail();
      }
    }

    return UserEntity.builder()
        .id(user.getId())
        .email(user.getEmail())
        .passwordHash(user.getPassword())
        .nickname(user.getNickname())
        .profileImageUrl(user.getProfileImageUrl())
        .providerUserId(providerUserId)
        .googleRefreshToken(user.getGoogleRefreshToken())
        .walletAddress(user.getWalletAddress())
        .provider(user.getAuthProvider())
        .role(user.getRole())
        .status(status)
        .deletedAt(status == UserStatus.ACTIVE ? null : user.getDeletedAt())
        .lastLoginAt(user.getLastLoginAt())
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .build();
  }

  /** 기존 영속 엔티티의 필드를 수정하는 방식으로 업데이트 (builder로 새 객체 만들지 말고, setter로 업데이트). */
  private void updateEntityFromDomain(UserEntity entity, User user) {
    UserStatus status = user.getStatus() != null ? user.getStatus() : UserStatus.ACTIVE;
    entity.setEmail(user.getEmail());
    entity.setPasswordHash(user.getPassword());
    entity.setNickname(user.getNickname());
    entity.setProfileImageUrl(user.getProfileImageUrl());
    entity.setProviderUserId(user.getProviderUserId());
    entity.setGoogleRefreshToken(user.getGoogleRefreshToken());
    entity.setWalletAddress(user.getWalletAddress());
    entity.setProvider(user.getAuthProvider());
    entity.setRole(user.getRole());
    entity.setStatus(status);
    entity.setDeletedAt(status == UserStatus.ACTIVE ? null : user.getDeletedAt());
    entity.setLastLoginAt(user.getLastLoginAt());
    entity.setUpdatedAt(user.getUpdatedAt());
  }

  private static UserStatus resolveStatus(UserEntity entity) {
    UserStatus status = entity.getStatus();
    if (status != null) {
      return status;
    }
    return entity.getDeletedAt() == null ? UserStatus.ACTIVE : UserStatus.DELETED;
  }
}
