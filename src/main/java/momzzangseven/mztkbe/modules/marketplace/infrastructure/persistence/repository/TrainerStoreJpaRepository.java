package momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.repository;

import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.entity.TrainerStoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for TrainerStoreEntity.
 *
 * <p>Infrastructure Layer: Provides database access methods.
 * Used by StorePersistenceAdapter (not directly by application layer).
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

  /**
   * PostgreSQL Native ON CONFLICT (Upsert) Query.
   *
   * <p>Avoids DataIntegrityViolationException caused by concurrent inserts.
   *
   * <p><b>Design Note:</b> PostgreSQL supports {@code RETURNING *} to retrieve the upserted row
   * in a single round-trip. However, Spring Data JPA's {@code @Modifying} annotation only supports
   * {@code void} or {@code int} return types, making it incompatible with {@code RETURNING}.
   * Therefore, the adapter performs a separate {@code findByTrainerId()} after this call.
   * For high-throughput batch scenarios, consider using {@code EntityManager.createNativeQuery()}
   * directly with {@code RETURNING *}.
   *
   * @param store The entity containing values
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      nativeQuery = true,
      value =
          "INSERT INTO trainer_stores ("
              + "  user_id, store_name, address, detail_address,"
              + "  location,"
              + "  phone_number, homepage_url, instagram_url, x_url,"
              + "  created_at, updated_at"
              + ") VALUES ("
              + "  :#{#store.trainerId}, :#{#store.storeName}, :#{#store.address}, :#{#store.detailAddress},"
              + "  ST_SetSRID(ST_MakePoint(:#{#store.longitude}, :#{#store.latitude}), 4326),"
              + "  :#{#store.phoneNumber}, :#{#store.homepageUrl}, :#{#store.instagramUrl}, :#{#store.xUrl},"
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
              + "  updated_at = CURRENT_TIMESTAMP")
  void upsertStore(@Param("store") TrainerStoreEntity store);
}
