package momzzangseven.mztkbe.modules.account.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.account.application.port.out.DeleteUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadManagedUserAccountStatusesPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.entity.UserAccountEntity;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.repository.UserAccountJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Persistence adapter implementing account output ports using {@link UserAccountJpaRepository}. */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAccountPersistenceAdapter
    implements LoadUserAccountPort,
        SaveUserAccountPort,
        DeleteUserAccountPort,
        LoadManagedUserAccountStatusesPort {

  private final UserAccountJpaRepository userAccountJpaRepository;

  // ========== LoadUserAccountPort ==========

  @Override
  @Transactional(readOnly = true)
  public Optional<UserAccount> findByUserId(Long userId) {
    log.debug("Loading account by userId: {}", userId);
    return userAccountJpaRepository.findByUserId(userId).map(this::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserAccount> findByProviderAndProviderUserId(
      AuthProvider provider, String providerUserId) {
    log.debug("Loading account by provider: {}, providerUserId: {}", provider, providerUserId);
    return userAccountJpaRepository
        .findByProviderAndProviderUserId(provider, providerUserId)
        .map(this::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserAccount> findActiveByEmail(String email) {
    log.debug("Loading ACTIVE account by email: {}", email);
    return userAccountJpaRepository
        .findByEmailAndStatus(email, AccountStatus.ACTIVE)
        .map(this::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserAccount> findDeletedByEmail(String email) {
    log.debug("Loading DELETED account by email: {}", email);
    return userAccountJpaRepository
        .findByEmailAndStatus(email, AccountStatus.DELETED)
        .map(this::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserAccount> findDeletedByProviderAndProviderUserId(
      AuthProvider provider, String providerUserId) {
    log.debug(
        "Loading DELETED account by provider: {}, providerUserId: {}", provider, providerUserId);
    return userAccountJpaRepository
        .findByProviderAndProviderUserIdAndStatus(provider, providerUserId, AccountStatus.DELETED)
        .map(this::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Long> findUserIdsForHardDeletion(Instant cutoff, int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be > 0");
    }
    return userAccountJpaRepository.findUserIdsByStatusAndDeletedAtBefore(
        AccountStatus.DELETED, cutoff, PageRequest.of(0, limit));
  }

  // ========== SaveUserAccountPort ==========

  @Override
  @Transactional
  public UserAccount save(UserAccount userAccount) {
    log.debug("Saving account for userId: {}", userAccount.getUserId());

    UserAccountEntity entity;

    if (userAccount.getId() != null) {
      // If the userAccount domain object has its own id, it means it is not a fresh user account.
      // Update the entity with new values.
      entity =
          userAccountJpaRepository
              .findById(userAccount.getId())
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "UserAccount not found with ID: " + userAccount.getId()));
      updateEntityFromDomain(entity, userAccount);
    } else {
      // Make new entity object for given userAccount
      entity = toEntity(userAccount);
    }

    UserAccountEntity saved = userAccountJpaRepository.save(entity);
    log.debug("Account saved with id: {}", saved.getId());
    return toDomain(saved);
  }

  // ========== DeleteUserAccountPort ==========

  @Override
  @Transactional
  public void deleteByUserId(Long userId) {
    log.debug("Hard-deleting account for userId: {}", userId);
    userAccountJpaRepository.deleteByUserId(userId);
  }

  @Override
  @Transactional
  public void deleteByUserIdIn(List<Long> userIds) {
    log.debug("Hard-deleting accounts for {} users", userIds.size());
    userAccountJpaRepository.deleteByUserIdIn(userIds);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<Long, AccountStatus> load(List<Long> userIds, AccountStatus statusFilter) {
    if (userIds != null && userIds.isEmpty()) {
      return Map.of();
    }
    if (statusFilter != null && userIds == null) {
      return userAccountJpaRepository.findManagedUserAccountStatusesByStatus(statusFilter).stream()
          .collect(
              Collectors.toMap(
                  UserAccountJpaRepository.ManagedUserAccountStatusProjection::getUserId,
                  UserAccountJpaRepository.ManagedUserAccountStatusProjection::getStatus));
    }

    Map<Long, AccountStatus> statuses =
        userAccountJpaRepository.findManagedUserAccountStatusesByUserIds(userIds).stream()
            .collect(
                Collectors.toMap(
                    UserAccountJpaRepository.ManagedUserAccountStatusProjection::getUserId,
                    UserAccountJpaRepository.ManagedUserAccountStatusProjection::getStatus));
    if (statusFilter == null) {
      return statuses;
    }
    return statuses.entrySet().stream()
        .filter(entry -> entry.getValue() == statusFilter)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  // ========== Mapping ==========

  private UserAccount toDomain(UserAccountEntity entity) {
    return UserAccount.builder()
        .id(entity.getId())
        .userId(entity.getUserId())
        .provider(entity.getProvider())
        .providerUserId(entity.getProviderUserId())
        .passwordHash(entity.getPasswordHash())
        .googleRefreshToken(entity.getGoogleRefreshToken())
        .lastLoginAt(entity.getLastLoginAt())
        .status(entity.getStatus())
        .deletedAt(entity.getDeletedAt())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  private UserAccountEntity toEntity(UserAccount domain) {
    return UserAccountEntity.builder()
        .id(domain.getId())
        .userId(domain.getUserId())
        .provider(domain.getProvider())
        .providerUserId(domain.getProviderUserId())
        .passwordHash(domain.getPasswordHash())
        .googleRefreshToken(domain.getGoogleRefreshToken())
        .lastLoginAt(domain.getLastLoginAt())
        .status(domain.getStatus() != null ? domain.getStatus() : AccountStatus.ACTIVE)
        .deletedAt(domain.getDeletedAt())
        .createdAt(domain.getCreatedAt())
        .updatedAt(domain.getUpdatedAt())
        .build();
  }

  private void updateEntityFromDomain(UserAccountEntity entity, UserAccount domain) {
    entity.setProvider(domain.getProvider());
    entity.setProviderUserId(domain.getProviderUserId());
    entity.setPasswordHash(domain.getPasswordHash());
    entity.setGoogleRefreshToken(domain.getGoogleRefreshToken());
    entity.setLastLoginAt(domain.getLastLoginAt());
    entity.setStatus(domain.getStatus() != null ? domain.getStatus() : AccountStatus.ACTIVE);
    entity.setDeletedAt(domain.getDeletedAt());
    entity.setUpdatedAt(domain.getUpdatedAt());
  }
}
