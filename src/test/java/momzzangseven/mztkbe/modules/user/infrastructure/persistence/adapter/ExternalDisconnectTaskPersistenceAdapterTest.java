package momzzangseven.mztkbe.modules.user.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.ExternalDisconnectStatus;
import momzzangseven.mztkbe.modules.user.domain.model.ExternalDisconnectTask;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.ExternalDisconnectTaskEntity;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.ExternalDisconnectTaskJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalDisconnectTaskPersistenceAdapter unit test")
class ExternalDisconnectTaskPersistenceAdapterTest {

  @Mock private ExternalDisconnectTaskJpaRepository repository;

  private ExternalDisconnectTaskPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new ExternalDisconnectTaskPersistenceAdapter(repository);
  }

  @Test
  @DisplayName("findDueTasks delegates query and maps entity fields")
  void findDueTasks_mapsEntityListToDomainList() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 19, 0);
    ExternalDisconnectTaskEntity entity =
        ExternalDisconnectTaskEntity.builder()
            .id(1L)
            .userId(100L)
            .provider(AuthProvider.GOOGLE)
            .providerUserId("provider-user")
            .encryptedToken("encrypted")
            .status(ExternalDisconnectStatus.PENDING)
            .attemptCount(2)
            .nextAttemptAt(now.minusMinutes(5))
            .lastError("last")
            .createdAt(now.minusDays(1))
            .updatedAt(now.minusHours(1))
            .build();
    when(repository.findDueTasks(
            eq(ExternalDisconnectStatus.PENDING), eq(now), any(Pageable.class)))
        .thenReturn(List.of(entity));

    List<ExternalDisconnectTask> tasks = adapter.findDueTasks(now, 25);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(repository)
        .findDueTasks(eq(ExternalDisconnectStatus.PENDING), eq(now), pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(25);

    assertThat(tasks).hasSize(1);
    ExternalDisconnectTask task = tasks.get(0);
    assertThat(task.getId()).isEqualTo(1L);
    assertThat(task.getUserId()).isEqualTo(100L);
    assertThat(task.getProvider()).isEqualTo(AuthProvider.GOOGLE);
    assertThat(task.getProviderUserId()).isEqualTo("provider-user");
    assertThat(task.getEncryptedToken()).isEqualTo("encrypted");
    assertThat(task.getStatus()).isEqualTo(ExternalDisconnectStatus.PENDING);
  }

  @Test
  @DisplayName("save maps domain to entity and returns mapped domain")
  void save_mapsBothDirections() {
    LocalDateTime now = LocalDateTime.of(2026, 2, 28, 19, 0);
    ExternalDisconnectTask task =
        ExternalDisconnectTask.builder()
            .userId(100L)
            .provider(AuthProvider.KAKAO)
            .providerUserId("kakao-uid")
            .encryptedToken(null)
            .status(ExternalDisconnectStatus.PENDING)
            .attemptCount(1)
            .nextAttemptAt(now.plusHours(1))
            .lastError("error")
            .createdAt(now.minusDays(1))
            .updatedAt(now)
            .build();

    when(repository.save(any(ExternalDisconnectTaskEntity.class)))
        .thenAnswer(
            invocation -> {
              ExternalDisconnectTaskEntity incoming = invocation.getArgument(0);
              return ExternalDisconnectTaskEntity.builder()
                  .id(55L)
                  .userId(incoming.getUserId())
                  .provider(incoming.getProvider())
                  .providerUserId(incoming.getProviderUserId())
                  .encryptedToken(incoming.getEncryptedToken())
                  .status(incoming.getStatus())
                  .attemptCount(incoming.getAttemptCount())
                  .nextAttemptAt(incoming.getNextAttemptAt())
                  .lastError(incoming.getLastError())
                  .createdAt(incoming.getCreatedAt())
                  .updatedAt(incoming.getUpdatedAt())
                  .build();
            });

    ExternalDisconnectTask saved = adapter.save(task);

    ArgumentCaptor<ExternalDisconnectTaskEntity> entityCaptor =
        ArgumentCaptor.forClass(ExternalDisconnectTaskEntity.class);
    verify(repository).save(entityCaptor.capture());
    assertThat(entityCaptor.getValue().getProvider()).isEqualTo(AuthProvider.KAKAO);
    assertThat(entityCaptor.getValue().getProviderUserId()).isEqualTo("kakao-uid");

    assertThat(saved.getId()).isEqualTo(55L);
    assertThat(saved.getProvider()).isEqualTo(AuthProvider.KAKAO);
    assertThat(saved.getProviderUserId()).isEqualTo("kakao-uid");
    assertThat(saved.getStatus()).isEqualTo(ExternalDisconnectStatus.PENDING);
  }

  @Test
  @DisplayName("deleteByUserIdIn delegates and returns repository count")
  void deleteByUserIdIn_delegates() {
    when(repository.deleteByUserIdIn(List.of(1L, 2L))).thenReturn(2);

    int deleted = adapter.deleteByUserIdIn(List.of(1L, 2L));

    assertThat(deleted).isEqualTo(2);
    verify(repository).deleteByUserIdIn(List.of(1L, 2L));
  }
}
