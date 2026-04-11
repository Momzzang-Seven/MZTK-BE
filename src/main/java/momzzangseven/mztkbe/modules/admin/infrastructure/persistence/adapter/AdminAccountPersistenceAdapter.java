package momzzangseven.mztkbe.modules.admin.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.application.port.out.CountActiveAdminAccountsPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.SaveAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.SoftDeleteAdminAccountsPort;
import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;
import momzzangseven.mztkbe.modules.admin.infrastructure.persistence.entity.AdminAccountEntity;
import momzzangseven.mztkbe.modules.admin.infrastructure.persistence.repository.AdminAccountJpaRepository;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter implementing all admin account output ports. Owns entity-to-domain and
 * domain-to-entity mapping.
 */
@Component
@RequiredArgsConstructor
public class AdminAccountPersistenceAdapter
    implements LoadAdminAccountPort,
        SaveAdminAccountPort,
        SoftDeleteAdminAccountsPort,
        CountActiveAdminAccountsPort {

  private final AdminAccountJpaRepository repository;

  @Override
  public Optional<AdminAccount> findActiveByUserId(Long userId) {
    return repository.findByUserIdAndDeletedAtIsNull(userId).map(this::toDomain);
  }

  @Override
  public Optional<AdminAccount> findActiveByLoginId(String loginId) {
    return repository.findByLoginIdAndDeletedAtIsNull(loginId).map(this::toDomain);
  }

  @Override
  public boolean existsByLoginId(String loginId) {
    return repository.existsByLoginIdAndDeletedAtIsNull(loginId);
  }

  @Override
  public List<AdminAccount> findAllActive() {
    return repository.findAllByDeletedAtIsNull().stream().map(this::toDomain).toList();
  }

  @Override
  public AdminAccount save(AdminAccount account) {
    AdminAccountEntity entity = toEntity(account);
    AdminAccountEntity saved = repository.save(entity);
    return toDomain(saved);
  }

  @Override
  public int softDeleteAll() {
    return repository.softDeleteAll(Instant.now());
  }

  @Override
  public long countActive() {
    return repository.countByDeletedAtIsNull();
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
