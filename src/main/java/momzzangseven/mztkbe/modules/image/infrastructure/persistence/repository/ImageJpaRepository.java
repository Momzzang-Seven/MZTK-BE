package momzzangseven.mztkbe.modules.image.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.entity.ImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImageJpaRepository extends JpaRepository<ImageEntity, Long> {
  Optional<ImageEntity> findByTmpObjectKey(String tmpObjectKey);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from ImageEntity i where i.tmpObjectKey = :tmpObjectKey")
  Optional<ImageEntity> findByTmpObjectKeyForUpdate(@Param("tmpObjectKey") String tmpObjectKey);

  /**
   * Deletes up to {@code batchSize} PENDING image records created before the given cutoff.
   *
   * <p>Uses a native subquery to select the target IDs first, then deletes them. This avoids
   * holding excessive row locks compared to a plain bulk DELETE.
   *
   * @param cutoff records with created_at before this instant are eligible
   * @param batchSize maximum number of rows deleted per call
   * @return the number of deleted rows
   */
  @Modifying(clearAutomatically = true)
  @Query(
      value =
          "DELETE FROM images "
              + "WHERE id IN ("
              + "  SELECT id FROM images "
              + "  WHERE status = 'PENDING' AND created_at < :cutoff "
              + "  LIMIT :batchSize"
              + ")",
      nativeQuery = true)
  int deletePendingBefore(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

  /**
   * Update the status and final object key of an image.
   *
   * @param id the id of the image
   * @param status the status of the image
   * @param finalObjectKey the final object key of the image
   * @return the number of rows updated
   */
  @Modifying(clearAutomatically = true)
  @Query(
      value =
          "UPDATE images SET status = :status, final_object_key = :finalObjectKey, "
              + "updated_at = NOW() WHERE id = :id",
      nativeQuery = true)
  int updateStatusAndFinalKey(
      @Param("id") Long id,
      @Param("status") String status,
      @Param("finalObjectKey") String finalObjectKey);
}
