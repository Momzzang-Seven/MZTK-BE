package momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.adapter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
 * <p>Uses {@code EntityManager} for the native upsert query to leverage PostgreSQL's {@code
 * RETURNING id}, eliminating the re-query race condition that existed when using {@code @Modifying}
 * + separate {@code findByTrainerId()}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorePersistenceAdapter implements LoadStorePort, SaveStorePort {

  private static final String UPSERT_SQL =
      "INSERT INTO trainer_stores ("
          + "  user_id, store_name, address, detail_address,"
          + "  location,"
          + "  phone_number, homepage_url, instagram_url, x_url,"
          + "  created_at, updated_at"
          + ") VALUES ("
          + "  :trainerId, :storeName, :address, :detailAddress,"
          + "  CASE WHEN :longitude IS NOT NULL AND :latitude IS NOT NULL"
          + "       THEN ST_SetSRID(ST_MakePoint(CAST(:longitude AS double precision),"
          + "            CAST(:latitude AS double precision)), 4326)"
          + "       ELSE NULL END,"
          + "  :phoneNumber, :homepageUrl, :instagramUrl, :xUrl,"
          + "  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP"
          + ") ON CONFLICT (user_id) DO UPDATE SET"
          + "  store_name = EXCLUDED.store_name,"
          + "  address = EXCLUDED.address,"
          + "  detail_address = EXCLUDED.detail_address,"
          + "  location = EXCLUDED.location,"
          + "  phone_number = EXCLUDED.phone_number,"
          + "  homepage_url = EXCLUDED.homepage_url,"
          + "  instagram_url = EXCLUDED.instagram_url,"
          + "  x_url = EXCLUDED.x_url,"
          + "  updated_at = CURRENT_TIMESTAMP"
          + " RETURNING id";

  private final TrainerStoreJpaRepository trainerStoreJpaRepository;

  @PersistenceContext private EntityManager entityManager;

  // ========== LoadStorePort Implementation ==========

  @Override
  public Optional<TrainerStore> findByTrainerId(Long trainerId) {
    log.debug("Loading store by trainerId: {}", trainerId);

    Optional<TrainerStoreEntity> entityOpt = trainerStoreJpaRepository.findByTrainerId(trainerId);

    return entityOpt.map(TrainerStoreEntity::toDomain);
  }

  // ========== SaveStorePort Implementation ==========

  /**
   * {@inheritDoc}
   *
   * <p>Implementation uses PostgreSQL native {@code INSERT ... ON CONFLICT ... DO UPDATE ...
   * RETURNING id} via {@code EntityManager} to:
   *
   * <ol>
   *   <li>Atomically handle concurrent upserts (database-level locking)
   *   <li>Retrieve the generated/existing ID in the same SQL statement
   *   <li>Avoid the re-query race condition of separate upsert + findByTrainerId calls
   * </ol>
   *
   * <p>After obtaining the ID, a standard JPA {@code findById()} retrieves the full entity with
   * proper Hibernate Spatial type mapping.
   */
  @Override
  public TrainerStore save(TrainerStore store) {
    log.debug("Saving store for trainerId: {}", store.getTrainerId());

    // Native upsert with RETURNING id — single atomic SQL statement
    Number idNumber =
        (Number)
            entityManager
                .createNativeQuery(UPSERT_SQL)
                .setParameter("trainerId", store.getTrainerId())
                .setParameter("storeName", store.getStoreName())
                .setParameter("address", store.getAddress())
                .setParameter("detailAddress", store.getDetailAddress())
                .setParameter("longitude", store.getLongitude())
                .setParameter("latitude", store.getLatitude())
                .setParameter("phoneNumber", store.getPhoneNumber())
                .setParameter("homepageUrl", store.getHomepageUrl())
                .setParameter("instagramUrl", store.getInstagramUrl())
                .setParameter("xUrl", store.getXUrl())
                .getSingleResult();

    Long id = idNumber.longValue();

    // Clear persistence context to ensure findById reads fresh data from DB
    entityManager.clear();

    // Re-read via JPA for proper Hibernate Spatial type mapping (Point ↔ geometry)
    TrainerStoreEntity savedEntity =
        trainerStoreJpaRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Store should exist after upsert but was not found. id: " + id));

    log.debug("Store saved with ID: {}", savedEntity.getId());

    return savedEntity.toDomain();
  }
}
