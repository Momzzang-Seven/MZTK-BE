package momzzangseven.mztkbe.modules.account.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.port.out.ExternalDisconnectTaskPort;
import momzzangseven.mztkbe.modules.account.domain.model.ExternalDisconnectStatus;
import momzzangseven.mztkbe.modules.account.domain.model.ExternalDisconnectTask;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.entity.ExternalDisconnectTaskEntity;
import momzzangseven.mztkbe.modules.account.infrastructure.persistence.repository.ExternalDisconnectTaskJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExternalDisconnectTaskPersistenceAdapter implements ExternalDisconnectTaskPort {

  private final ExternalDisconnectTaskJpaRepository repository;

  @Override
  public List<ExternalDisconnectTask> findDueTasks(Instant now, int limit) {
    return repository
        .findDueTasks(ExternalDisconnectStatus.PENDING, now, PageRequest.of(0, limit))
        .stream()
        .map(ExternalDisconnectTaskPersistenceAdapter::toDomain)
        .toList();
  }

  @Override
  public ExternalDisconnectTask save(ExternalDisconnectTask task) {
    ExternalDisconnectTaskEntity entity = toEntity(task);
    ExternalDisconnectTaskEntity saved = repository.save(entity);
    return toDomain(saved);
  }

  @Override
  public int deleteByUserIdIn(List<Long> userIds) {
    return repository.deleteByUserIdIn(userIds);
  }

  @Override
  public int deleteByStatusAndUpdatedAtBefore(ExternalDisconnectStatus status, Instant cutoff) {
    return repository.deleteByStatusAndUpdatedAtBefore(status, cutoff);
  }

  private static ExternalDisconnectTask toDomain(ExternalDisconnectTaskEntity entity) {
    return ExternalDisconnectTask.builder()
        .id(entity.getId())
        .userId(entity.getUserId())
        .provider(entity.getProvider())
        .providerUserId(entity.getProviderUserId())
        .encryptedToken(entity.getEncryptedToken())
        .status(entity.getStatus())
        .attemptCount(entity.getAttemptCount())
        .nextAttemptAt(entity.getNextAttemptAt())
        .lastError(entity.getLastError())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  private static ExternalDisconnectTaskEntity toEntity(ExternalDisconnectTask task) {
    return ExternalDisconnectTaskEntity.builder()
        .id(task.getId())
        .userId(task.getUserId())
        .provider(task.getProvider())
        .providerUserId(task.getProviderUserId())
        .encryptedToken(task.getEncryptedToken())
        .status(task.getStatus())
        .attemptCount(task.getAttemptCount())
        .nextAttemptAt(task.getNextAttemptAt())
        .lastError(task.getLastError())
        .createdAt(task.getCreatedAt())
        .updatedAt(task.getUpdatedAt())
        .build();
  }
}
