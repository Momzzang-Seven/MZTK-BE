package momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.adapter;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadStorePort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveStorePort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.TrainerStore;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.entity.TrainerStoreEntity;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.repository.TrainerStoreJpaRepository;
import org.springframework.stereotype.Component;

/** Persistence adapter implementing store port interfaces using TrainerStoreJpaRepository. */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorePersistenceAdapter implements LoadStorePort, SaveStorePort {

  private final TrainerStoreJpaRepository trainerStoreJpaRepository;

  // ========== LoadStorePort Implementation ==========

  @Override
  public Optional<TrainerStore> findByTrainerId(Long trainerId) {
    log.debug("Loading store by trainerId: {}", trainerId);

    Optional<TrainerStoreEntity> entityOpt = trainerStoreJpaRepository.findByTrainerId(trainerId);

    return entityOpt.map(TrainerStoreEntity::toDomain);
  }

  // ========== SaveStorePort Implementation ==========

  @Override
  public TrainerStore save(TrainerStore store) {
    log.debug("Saving store for trainerId: {}", store.getTrainerId());

    TrainerStoreEntity entity = TrainerStoreEntity.fromDomain(store);
    trainerStoreJpaRepository.upsertStore(entity);

    // Native upsert does not return the generated ID, so we re-query by trainerId
    TrainerStoreEntity savedEntity = trainerStoreJpaRepository.findByTrainerId(store.getTrainerId())
        .orElseThrow(() -> new IllegalStateException("Store should exist after upsert but was not found. trainerId: " + store.getTrainerId()));

    log.debug("Store saved with ID: {}", savedEntity.getId());

    return savedEntity.toDomain();
  }
}
