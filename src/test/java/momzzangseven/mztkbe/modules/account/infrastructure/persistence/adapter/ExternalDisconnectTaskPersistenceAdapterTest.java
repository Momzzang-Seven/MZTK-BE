package momzzangseven.mztkbe.modules.account.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import momzzangseven.mztkbe.modules.account.domain.model.ExternalDisconnectStatus;
import momzzangseven.mztkbe.modules.account.domain.model.ExternalDisconnectTask;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.entity.ExternalDisconnectTaskEntity;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.repository.ExternalDisconnectTaskJpaRepository;
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
    Instant now = Instant.parse("2026-02-28T19:00:00Z");
    ExternalDisconnectTaskEntity entity =
        ExternalDisconnectTaskEntity.builder()
            .id(1L)
            .userId(100L)
            .provider(AuthProvider.GOOGLE)
            .providerUserId("provider-user")
            .encryptedToken("encrypted")
            .status(ExternalDisconnectStatus.PENDING)
            .attemptCount(2)
            .nextAttemptAt(now.minus(5, ChronoUnit.MINUTES))
            .lastError("last")
            .createdAt(now.minus(1, ChronoUnit.DAYS))
            .updatedAt(now.minus(1, ChronoUnit.HOURS))
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
    Instant now = Instant.parse("2026-02-28T19:00:00Z");
    ExternalDisconnectTask task =
        ExternalDisconnectTask.builder()
            .userId(100L)
            .provider(AuthProvider.KAKAO)
            .providerUserId("kakao-uid")
            .encryptedToken(null)
            .status(ExternalDisconnectStatus.PENDING)
            .attemptCount(1)
            .nextAttemptAt(now.plus(1, ChronoUnit.HOURS))
            .lastError("error")
            .createdAt(now.minus(1, ChronoUnit.DAYS))
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
  @DisplayName("[M-146] deleteByStatusAndUpdatedAtBefore delegates and returns count")
  void deleteByStatusAndUpdatedAtBefore_delegates() {
    Instant cutoff = Instant.parse("2026-02-21T00:00:00Z");
    when(repository.deleteByStatusAndUpdatedAtBefore(ExternalDisconnectStatus.SUCCESS, cutoff))
        .thenReturn(5);

    int deleted =
        adapter.deleteByStatusAndUpdatedAtBefore(ExternalDisconnectStatus.SUCCESS, cutoff);

    assertThat(deleted).isEqualTo(5);
    verify(repository).deleteByStatusAndUpdatedAtBefore(ExternalDisconnectStatus.SUCCESS, cutoff);
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
