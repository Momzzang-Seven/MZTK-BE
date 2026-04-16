package momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.repository;

import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.entity.MarketplaceClassEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link MarketplaceClassEntity}.
 *
 * <p>Used exclusively by {@code ClassPersistenceAdapter}. Dynamic queries (with filtering and
 * sorting) are handled via QueryDSL in the adapter.
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
}
