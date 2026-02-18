package momzzangseven.mztkbe.modules.level.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.level.application.port.out.UserProgressPort;
import momzzangseven.mztkbe.modules.level.domain.model.UserProgress;
import momzzangseven.mztkbe.modules.level.infrastructure.persistence.entity.UserProgressEntity;
import momzzangseven.mztkbe.modules.level.infrastructure.repository.UserProgressJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserProgressPersistenceAdapter implements UserProgressPort {

  private final UserProgressJpaRepository userProgressJpaRepository;
  private final PlatformTransactionManager transactionManager;

  @Override
  @Transactional(readOnly = true)
  public Optional<UserProgress> loadUserProgress(Long userId) {
    return userProgressJpaRepository.findById(userId).map(UserProgressEntity::toDomain);
  }

  @Override
  @Transactional
  public UserProgress loadUserProgressWithLock(Long userId) {
    return userProgressJpaRepository
        .findByUserIdForUpdate(userId)
        .map(UserProgressEntity::toDomain)
        .orElseThrow(() -> new IllegalStateException("UserProgress not found: userId=" + userId));
  }

  @Override
  @Transactional
  public UserProgress loadOrCreateUserProgress(Long userId) {
    Optional<UserProgressEntity> existing = userProgressJpaRepository.findById(userId);
    if (existing.isPresent()) {
      return existing.get().toDomain();
    }

    TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
    requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    Boolean created =
        requiresNew.execute(
            status -> {
              try {
                userProgressJpaRepository.saveAndFlush(
                    UserProgressEntity.from(UserProgress.createInitial(userId)));
                return true;
              } catch (DataIntegrityViolationException e) {
                status.setRollbackOnly();
                return false;
              }
            });

    if (Boolean.TRUE.equals(created)) {
      return userProgressJpaRepository
          .findById(userId)
          .map(UserProgressEntity::toDomain)
          .orElseThrow();
    }

    log.info("UserProgress already created concurrently: userId={}", userId);
    return userProgressJpaRepository
        .findById(userId)
        .map(UserProgressEntity::toDomain)
        .orElseThrow();
  }

  @Override
  @Transactional
  public UserProgress saveUserProgress(UserProgress progress) {
    UserProgressEntity entity =
        userProgressJpaRepository
            .findById(progress.getUserId())
            .orElseGet(() -> UserProgressEntity.from(progress));

    entity.setLevel(progress.getLevel());
    entity.setAvailableXp(progress.getAvailableXp());
    entity.setLifetimeXp(progress.getLifetimeXp());
    entity.setUpdatedAt(progress.getUpdatedAt());

    return userProgressJpaRepository.save(entity).toDomain();
  }
}
