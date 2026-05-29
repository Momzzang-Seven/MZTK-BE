package momzzangseven.mztkbe.modules.admin.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.application.port.out.CountActiveAdminAccountsPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.DeleteAdminAccountsPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.SaveAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.domain.event.AdminAccountInvalidatedEvent;
import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;
import momzzangseven.mztkbe.modules.admin.infrastructure.persistence.entity.AdminAccountEntity;
import momzzangseven.mztkbe.modules.admin.infrastructure.persistence.repository.AdminAccountJpaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter implementing all admin account output ports. Owns entity-to-domain and
 * domain-to-entity mapping.
 */
@Component
@RequiredArgsConstructor
public class AdminAccountPersistenceAdapter
    implements LoadAdminAccountPort,
        SaveAdminAccountPort,
        DeleteAdminAccountsPort,
        CountActiveAdminAccountsPort {

  private final AdminAccountJpaRepository repository;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional(readOnly = true)
  public Optional<AdminAccount> findActiveByUserId(Long userId) {
    return repository.findByUserIdAndDeletedAtIsNull(userId).map(this::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AdminAccount> findActiveByLoginId(String loginId) {
    return repository.findByLoginIdAndDeletedAtIsNull(loginId).map(this::toDomain);
  }

  @Override
  @Transactional
  public Optional<AdminAccount> findActiveByLoginIdForUpdate(String loginId) {
    return repository.findByLoginIdAndDeletedAtIsNullForUpdate(loginId).map(this::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsByLoginId(String loginId) {
    return repository.existsByLoginIdAndDeletedAtIsNull(loginId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<AdminAccount> findAllActive() {
    return repository.findAllByDeletedAtIsNull().stream().map(this::toDomain).toList();
  }

  @Override
  @Transactional
  public AdminAccount save(AdminAccount account) {
    AdminAccountEntity entity = toEntity(account);
    AdminAccountEntity saved = repository.save(entity);
    eventPublisher.publishEvent(new AdminAccountInvalidatedEvent(saved.getUserId()));
    return toDomain(saved);
  }

  @Override
  @Transactional
  public AdminAccount saveAndFlush(AdminAccount account) {
    AdminAccountEntity entity = toEntity(account);
    AdminAccountEntity saved = repository.saveAndFlush(entity);
    eventPublisher.publishEvent(new AdminAccountInvalidatedEvent(saved.getUserId()));
    return toDomain(saved);
  }

  @Override
  @Transactional
  public List<Long> deleteAllAndReturnUserIds() {
    List<Long> userIds = repository.findAll().stream().map(AdminAccountEntity::getUserId).toList();
    repository.deleteAllInBulk();
    for (Long userId : userIds) {
      eventPublisher.publishEvent(new AdminAccountInvalidatedEvent(userId));
    }
    return userIds;
  }

  @Override
  @Transactional(readOnly = true)
  public long countActive() {
    return repository.countByDeletedAtIsNull();
  }

  @Override
  @Transactional(readOnly = true)
  public long countActiveByRole(String roleName) {
    return repository.countActiveByRole(roleName);
  }

  private AdminAccountEntity toEntity(AdminAccount domain) {
    return AdminAccountEntity.builder()
        .id(domain.getId())
        .userId(domain.getUserId())
        .loginId(domain.getLoginId())
        .passwordHash(domain.getPasswordHash())
        .createdBy(domain.getCreatedBy())
        .lastLoginAt(domain.getLastLoginAt())
        .passwordLastRotatedAt(domain.getPasswordLastRotatedAt())
        .deletedAt(domain.getDeletedAt())
        .createdAt(domain.getCreatedAt())
        .updatedAt(domain.getUpdatedAt())
        .build();
  }

  private AdminAccount toDomain(AdminAccountEntity entity) {
    return AdminAccount.builder()
        .id(entity.getId())
        .userId(entity.getUserId())
        .loginId(entity.getLoginId())
        .passwordHash(entity.getPasswordHash())
        .createdBy(entity.getCreatedBy())
        .lastLoginAt(entity.getLastLoginAt())
        .passwordLastRotatedAt(entity.getPasswordLastRotatedAt())
        .deletedAt(entity.getDeletedAt())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
