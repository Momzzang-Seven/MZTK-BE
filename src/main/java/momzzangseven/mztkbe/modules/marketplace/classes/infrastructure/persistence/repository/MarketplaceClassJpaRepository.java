package momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.repository;

import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity.MarketplaceClassEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link MarketplaceClassEntity}.
 *
 * <p>Used exclusively by {@code ClassPersistenceAdapter}. Dynamic queries (with filtering and
 * sorting) are handled via QueryDSL in the adapter.
 *
 * <p>Cross-module data (e.g. trainer store info) is fetched separately via output ports ({@link
 * momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadTrainerStorePort}), not
 * via JOIN in this repository, to respect hexagonal architecture module boundaries.
 */
@Repository
public interface MarketplaceClassJpaRepository extends JpaRepository<MarketplaceClassEntity, Long> {

  /**
   * Find all active classes, ordered by creation date descending.
   *
   * <p>Used by the public listing endpoint. No in-memory filtering.
   *
   * @param pageable pagination info
   * @return paged result of active entities
   */
  Page<MarketplaceClassEntity> findByActiveTrueOrderByCreatedAtDesc(Pageable pageable);

  /**
   * Find all classes for a given trainer (active and inactive), ordered by creation date
   * descending.
   *
   * @param trainerId trainer ID
   * @param pageable pagination info
   * @return paged result of entities
   */
  Page<MarketplaceClassEntity> findByTrainerIdOrderByCreatedAtDesc(
      Long trainerId, Pageable pageable);

  /**
   * Find a single active class by its ID.
   *
   * <p>Used by the public class detail endpoint. Store information is loaded separately via {@code
   * LoadTrainerStorePort} in the adapter to respect cross-module boundaries — {@code
   * TrainerStoreEntity} must not be referenced in this repository's queries.
   *
   * @param classId class ID
   * @return Optional containing the entity if found and active
   */
  Optional<MarketplaceClassEntity> findByIdAndActiveTrue(Long classId);
}
