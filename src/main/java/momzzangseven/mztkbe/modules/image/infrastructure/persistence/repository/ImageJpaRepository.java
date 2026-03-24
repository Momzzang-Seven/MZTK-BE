package momzzangseven.mztkbe.modules.image.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.entity.ImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImageJpaRepository extends JpaRepository<ImageEntity, Long> {
  Optional<ImageEntity> findByTmpObjectKey(String tmpObjectKey);

  // ========== SELECT ========== //

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from ImageEntity i where i.tmpObjectKey = :tmpObjectKey")
  Optional<ImageEntity> findByTmpObjectKeyForUpdate(@Param("tmpObjectKey") String tmpObjectKey);

  /** Returns all images whose referenceType is in the given set and referenceId matches. */
  List<ImageEntity> findAllByReferenceTypeInAndReferenceIdOrderByImgOrder(
      List<String> referenceTypes, Long referenceId);

  /**
   * Returns all images whose referenceType is in the given set and referenceId is one of the IDs.
   */
  List<ImageEntity> findAllByReferenceTypeInAndReferenceIdInOrderByReferenceIdAscImgOrderAsc(
      List<String> referenceTypes, List<Long> referenceIds);

  /** Returns images matching the given IDs (no lock). */
  List<ImageEntity> findAllByIdIn(List<Long> ids);

  /** Returns images matching the given IDs under PESSIMISTIC_WRITE lock. */
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM ImageEntity i WHERE i.id IN :ids")
  List<ImageEntity> findAllByIdInForUpdate(@Param("ids") List<Long> ids);

  /**
   * Returns up to {@code batchSize} unlinked images (reference_type IS NULL AND reference_id IS
   * NULL) created before the given cutoff, ordered by id ascending.
   *
   * <p>Uses a native query for the IS NULL predicate which is not expressible via derived query
   * method names in Spring Data JPA.
   */
  @Query(
      value =
          "SELECT * FROM images "
              + "WHERE reference_type IS NULL AND reference_id IS NULL "
              + "AND updated_at < :cutoff "
              + "ORDER BY id "
              + "LIMIT :batchSize",
      nativeQuery = true)
  List<ImageEntity> findUnlinkedBefore(
      @Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

  @Query(
      value =
          "SELECT i.* FROM images i "
              + "LEFT JOIN answers a ON a.id = i.reference_id "
              + "WHERE i.reference_type = 'COMMUNITY_ANSWER' "
              + "AND i.reference_id IS NOT NULL "
              + "AND a.id IS NULL "
              + "ORDER BY i.id "
              + "LIMIT :batchSize",
      nativeQuery = true)
  List<ImageEntity> findOrphanAnswerImages(@Param("batchSize") int batchSize);

  @Query(
      value =
          "SELECT i.* FROM images i "
              + "LEFT JOIN posts p ON p.id = i.reference_id "
              + "WHERE i.reference_type IN ('COMMUNITY_FREE', 'COMMUNITY_QUESTION') "
              + "AND i.reference_id IS NOT NULL "
              + "AND p.id IS NULL "
              + "ORDER BY i.id "
              + "LIMIT :batchSize",
      nativeQuery = true)
  List<ImageEntity> findOrphanPostImages(@Param("batchSize") int batchSize);

  // ========== DELETE ========== //

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
   * Permanently removes image records by ID. Called by the cleanup service after the corresponding
   * S3 objects have been removed.
   */
  @Modifying(clearAutomatically = true)
  @Query(value = "DELETE FROM images WHERE id IN (:ids)", nativeQuery = true)
  void deleteByIdIn(@Param("ids") List<Long> ids);

  // ========== UPDATE ========== //

  /**
   * Update the status, final object key, and error reason of an image.
   *
   * @param id the id of the image
   * @param status the new status (COMPLETED or FAILED)
   * @param finalObjectKey the S3 key of the converted image; null when status is FAILED
   * @param errorReason the reason for failure; null when status is COMPLETED
   * @return the number of rows updated
   */
  @Modifying(clearAutomatically = true)
  @Query(
      value =
          "UPDATE images SET status = :status, final_object_key = :finalObjectKey, "
              + "error_reason = :errorReason, updated_at = NOW() WHERE id = :id",
      nativeQuery = true)
  int updateStatusFinalKeyAndErrorReason(
      @Param("id") Long id,
      @Param("status") String status,
      @Param("finalObjectKey") String finalObjectKey,
      @Param("errorReason") String errorReason);

  /**
   * Sets reference_type and reference_id to NULL for all images whose referenceType is in the given
   * set and referenceId matches. Does not physically delete the rows.
   */
  @Modifying(clearAutomatically = true)
  @Query(
      value =
          "UPDATE images "
              + "SET reference_type = NULL, reference_id = NULL, updated_at = NOW() "
              + "WHERE reference_type IN (:referenceTypes) AND reference_id = :referenceId",
      nativeQuery = true)
  void unlinkByReferenceTypeInAndReferenceId(
      @Param("referenceTypes") List<String> referenceTypes, @Param("referenceId") Long referenceId);

  /**
   * Sets reference_type and reference_id to NULL for the specified image IDs. Does not physically
   * delete the rows.
   */
  @Modifying(clearAutomatically = true)
  @Query(
      value =
          "UPDATE images "
              + "SET reference_type = NULL, reference_id = NULL, updated_at = NOW() "
              + "WHERE id IN (:ids)",
      nativeQuery = true)
  void unlinkByIdIn(@Param("ids") List<Long> ids);
}
