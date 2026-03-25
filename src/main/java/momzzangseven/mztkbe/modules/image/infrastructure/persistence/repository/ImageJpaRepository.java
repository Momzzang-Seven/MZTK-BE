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
   * Same as {@link #findAllByReferenceTypeInAndReferenceIdOrderByImgOrder} but holds a
   * PESSIMISTIC_WRITE lock on every returned row. Rows are fetched in primary-key order by the DB
   * index scan, giving a consistent lock-acquisition order across concurrent transactions and
   * preventing cross-reference deadlocks.
   */
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT i FROM ImageEntity i "
          + "WHERE i.referenceType IN :referenceTypes AND i.referenceId = :referenceId "
          + "ORDER BY i.id")
  List<ImageEntity> findAllByReferenceTypeInAndReferenceIdForUpdate(
      @Param("referenceTypes") List<String> referenceTypes, @Param("referenceId") Long referenceId);

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
   * Returns up to {@code batchSize} non-PENDING images with {@code reference_id IS NULL} whose
   * {@code updated_at} is before the given cutoff, ordered by id ascending.
   *
   * <p>These are images that were once linked (COMPLETED or FAILED) but whose owning reference was
   * deleted or updated. PENDING images with null {@code reference_id} are handled separately by
   * {@code ImagePendingCleanupService}.
   */
  @Query(
      value =
          "SELECT * FROM images "
              + "WHERE status != 'PENDING' "
              + "AND reference_id IS NULL "
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
   * Detaches all images belonging to the given reference: sets {@code reference_id = NULL} and
   * updates {@code updated_at}. {@code reference_type} and {@code status} are intentionally
   * preserved so that:
   *
   * <ul>
   *   <li>PENDING images remain available for the in-flight Lambda callback.
   *   <li>COMPLETED/FAILED images retain their type so they can be re-linked by the same user.
   * </ul>
   */
  @Modifying(clearAutomatically = true)
  @Query(
      value =
          "UPDATE images "
              + "SET reference_id = NULL, updated_at = NOW() "
              + "WHERE reference_type IN (:referenceTypes) AND reference_id = :referenceId",
      nativeQuery = true)
  void unlinkByReferenceTypeInAndReferenceId(
      @Param("referenceTypes") List<String> referenceTypes, @Param("referenceId") Long referenceId);

  /**
   * Detaches specific images by ID: sets {@code reference_id = NULL} and updates {@code
   * updated_at}. {@code reference_type} and {@code status} are intentionally preserved (see {@link
   * #unlinkByReferenceTypeInAndReferenceId}).
   */
  @Modifying(clearAutomatically = true)
  @Query(
      value = "UPDATE images SET reference_id = NULL, updated_at = NOW() WHERE id IN (:ids)",
      nativeQuery = true)
  void unlinkByIdIn(@Param("ids") List<Long> ids);
}
