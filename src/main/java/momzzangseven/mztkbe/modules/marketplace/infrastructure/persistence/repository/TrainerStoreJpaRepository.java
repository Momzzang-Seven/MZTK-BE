package momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.repository;

import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.entity.TrainerStoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for TrainerStoreEntity.
 *
 * <p>Infrastructure Layer: Provides standard database access methods. Used by
 * StorePersistenceAdapter (not directly by application layer).
 *
 * <p><b>Note:</b> The native upsert query is implemented in {@code StorePersistenceAdapter}
 * using {@code EntityManager} to leverage {@code RETURNING id}, which is incompatible with
 * Spring Data JPA's {@code @Modifying} annotation (only supports void/int return types).
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
