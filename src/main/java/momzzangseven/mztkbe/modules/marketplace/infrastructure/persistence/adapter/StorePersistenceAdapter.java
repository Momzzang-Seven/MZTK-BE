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

/**
 * Persistence adapter implementing store port interfaces.
 *
 * <p>Thin JPA pass-through following the project convention (see {@code WalletPersistenceAdapter}).
 * Domain ↔ Entity conversion is the only responsibility; all business branching logic (create vs.
 * update) lives in the application service layer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorePersistenceAdapter implements LoadStorePort, SaveStorePort {

  private final TrainerStoreJpaRepository trainerStoreJpaRepository;

  // ========== LoadStorePort Implementation ==========

  @Override
  public Optional<TrainerStore> findByTrainerId(Long trainerId) {
    log.debug("Loading store by trainerId: {}", trainerId);
    return trainerStoreJpaRepository.findByTrainerId(trainerId).map(TrainerStoreEntity::toDomain);
  }

  // ========== SaveStorePort Implementation ==========

  /**
   * Save a trainer store via standard JPA persist/merge.
   *
   * <p>When {@code store.getId()} is null, JPA performs an INSERT. When non-null, JPA performs a
   * merge (UPDATE). The application service is responsible for setting the ID appropriately before
   * calling this method.
   *
   * @param store domain model to save
   * @return saved domain model with generated ID and timestamps
   */
  @Override
  public TrainerStore save(TrainerStore store) {
    log.debug("Saving store for trainerId: {}", store.getTrainerId());

    TrainerStoreEntity entity = TrainerStoreEntity.fromDomain(store);
    TrainerStoreEntity savedEntity = trainerStoreJpaRepository.save(entity);

    log.debug("Store saved with ID: {}", savedEntity.getId());
    return savedEntity.toDomain();
  }
}
