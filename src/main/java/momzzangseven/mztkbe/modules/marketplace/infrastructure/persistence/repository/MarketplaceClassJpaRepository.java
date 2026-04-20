package momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.repository;

import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.entity.MarketplaceClassEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

  /**
   * Load a single class together with the trainer's store in one query (LEFT JOIN).
   *
   * <p>Returns an {@code Object[2]} array where {@code [0]} is the {@link MarketplaceClassEntity}
   * and {@code [1]} is the {@link TrainerStoreEntity} (may be {@code null} when the trainer has no
   * store registered yet).
   *
   * <p><b>Note on {@code active = true}</b>: this query is used exclusively by the public class
   * detail endpoint, which must only expose active classes. Inactive-class detail (e.g., a
   * trainer's own preview including inactive listings) is not yet implemented. If that requirement
   * arises, add a separate query or remove this filter.
   *
   * @param classId class ID
   * @return list containing the joined projection, or empty list if the class does not exist
   */
  @Query(
      """
      SELECT mc, ts
      FROM MarketplaceClassEntity mc
      LEFT JOIN TrainerStoreEntity ts ON ts.trainerId = mc.trainerId
      WHERE mc.id = :classId
        AND mc.active = true
      """)
  List<Object[]> findClassWithStore(@Param("classId") Long classId);
}
