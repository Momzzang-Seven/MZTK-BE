package momzzangseven.mztkbe.modules.user.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadExternalDisconnectTaskPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveExternalDisconnectTaskPort;
import momzzangseven.mztkbe.modules.user.domain.model.ExternalDisconnectStatus;
import momzzangseven.mztkbe.modules.user.domain.model.ExternalDisconnectTask;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.entity.ExternalDisconnectTaskEntity;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.ExternalDisconnectTaskJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExternalDisconnectTaskPersistenceAdapter
    implements LoadExternalDisconnectTaskPort, SaveExternalDisconnectTaskPort {

  private final ExternalDisconnectTaskJpaRepository repository;

  @Override
  public List<ExternalDisconnectTask> findDueTasks(LocalDateTime now, int limit) {
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
