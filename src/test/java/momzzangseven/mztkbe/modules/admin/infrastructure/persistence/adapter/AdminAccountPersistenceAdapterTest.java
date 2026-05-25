package momzzangseven.mztkbe.modules.admin.infrastructure.persistence.adapter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import momzzangseven.mztkbe.modules.admin.domain.event.AdminAccountInvalidatedEvent;
import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;
import momzzangseven.mztkbe.modules.admin.infrastructure.persistence.entity.AdminAccountEntity;
import momzzangseven.mztkbe.modules.admin.infrastructure.persistence.repository.AdminAccountJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Verifies that admin account writes publish {@link AdminAccountInvalidatedEvent} so the auth-path
 * cache can drop its stale entry. Other persistence behavior (mapping, queries) is unchanged by
 * [MOM-460] and not re-tested here.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAccountPersistenceAdapter — cache invalidation events")
class AdminAccountPersistenceAdapterTest {

  @Mock private AdminAccountJpaRepository repository;
  @Mock private ApplicationEventPublisher eventPublisher;

  private AdminAccountPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new AdminAccountPersistenceAdapter(repository, eventPublisher);
  }

  @Test
  @DisplayName("save publishes AdminAccountInvalidatedEvent with saved userId")
  void savePublishesEvent() {
    AdminAccount account = newAccount(10L);
    when(repository.save(any())).thenReturn(entity(1L, 10L));

    adapter.save(account);

    verify(eventPublisher).publishEvent(new AdminAccountInvalidatedEvent(10L));
  }

  @Test
  @DisplayName("saveAndFlush publishes AdminAccountInvalidatedEvent with saved userId")
  void saveAndFlushPublishesEvent() {
    AdminAccount account = newAccount(11L);
    when(repository.saveAndFlush(any())).thenReturn(entity(2L, 11L));

    adapter.saveAndFlush(account);

    verify(eventPublisher).publishEvent(new AdminAccountInvalidatedEvent(11L));
  }

  @Test
  @DisplayName("deleteAllAndReturnUserIds publishes one event per userId")
  void deleteAllPublishesEventPerUserId() {
    when(repository.findAll()).thenReturn(List.of(entity(1L, 100L), entity(2L, 200L)));

    adapter.deleteAllAndReturnUserIds();

    verify(eventPublisher).publishEvent(new AdminAccountInvalidatedEvent(100L));
    verify(eventPublisher).publishEvent(new AdminAccountInvalidatedEvent(200L));
  }

  private AdminAccount newAccount(Long userId) {
    Instant now = Instant.now();
    return AdminAccount.builder()
        .userId(userId)
        .loginId("admin-" + userId)
        .passwordHash("$2a$hash")
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  private AdminAccountEntity entity(Long id, Long userId) {
    Instant now = Instant.now();
    return AdminAccountEntity.builder()
        .id(id)
        .userId(userId)
        .loginId("admin-" + userId)
        .passwordHash("$2a$hash")
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
