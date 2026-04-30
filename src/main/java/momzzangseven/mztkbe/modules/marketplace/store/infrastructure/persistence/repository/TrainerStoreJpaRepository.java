package momzzangseven.mztkbe.modules.marketplace.store.infrastructure.persistence.repository;

import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.store.infrastructure.persistence.entity.TrainerStoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for TrainerStoreEntity.
 *
 * <p>Infrastructure Layer: Provides standard database access methods. Used exclusively by {@code
 * StorePersistenceAdapter} (not directly by the application layer).
 */
@Repository
public interface TrainerStoreJpaRepository extends JpaRepository<TrainerStoreEntity, Long> {

  /**
   * Find a store entity by trainer's user ID.
   *
   * @param trainerId trainer's user ID
   * @return Optional containing the store entity if found
   */
  Optional<TrainerStoreEntity> findByTrainerId(Long trainerId);
}
