package momzzangseven.mztkbe.modules.answer.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerDeleteStatus;
import momzzangseven.mztkbe.modules.answer.domain.vo.AnswerPublicationStatus;
import momzzangseven.mztkbe.modules.answer.infrastructure.persistence.entity.AnswerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AnswerJpaRepository extends JpaRepository<AnswerEntity, Long> {

  List<AnswerEntity> findByPostIdOrderByIsAcceptedDescCreatedAtAsc(Long postId);

  List<AnswerEntity>
      findByPostIdAndPublicationStatusAndPendingDeleteStatusIsNullOrderByIsAcceptedDescCreatedAtAsc(
          Long postId, AnswerPublicationStatus publicationStatus);

  @Query(
      """
      select a from AnswerEntity a
      where a.postId = :postId
        and (
          (a.publicationStatus = :visibleStatus and a.pendingDeleteStatus is null)
          or (
            :ownerUserId is not null
            and a.userId = :ownerUserId
            and a.createPreparationToken is null
            and (
              a.publicationStatus in :ownerVisibleStatuses
              or (a.publicationStatus = :visibleStatus and a.pendingDeleteStatus is not null)
            )
          )
        )
      order by a.isAccepted desc, a.createdAt asc
      """)
  List<AnswerEntity> findPublicAndOwnerVisibleByPostId(
      @Param("postId") Long postId,
      @Param("ownerUserId") Long ownerUserId,
      @Param("visibleStatus") AnswerPublicationStatus visibleStatus,
      @Param("ownerVisibleStatuses") List<AnswerPublicationStatus> ownerVisibleStatuses);

  long countByPostId(Long postId);

  long countByPostIdAndPublicationStatusAndPendingDeleteStatusIsNull(
      Long postId, AnswerPublicationStatus publicationStatus);

  long countByPostIdAndPublicationStatus(Long postId, AnswerPublicationStatus publicationStatus);

  @Query(
      """
      select a.postId as postId, count(a.id) as answerCount
      from AnswerEntity a
      where a.postId in :postIds
        and a.publicationStatus = :visibleStatus
        and a.pendingDeleteStatus is null
      group by a.postId
      """)
  List<PostAnswerCount> countAnswersByPostIds(
      @Param("postIds") List<Long> postIds,
      @Param("visibleStatus") AnswerPublicationStatus visibleStatus);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from AnswerEntity a where a.id = :answerId")
  Optional<AnswerEntity> findByIdForUpdate(@Param("answerId") Long answerId);

  @Query(
      """
      select case when count(a) > 0 then true else false end from AnswerEntity a
      where a.postId = :postId
        and (
          a.createPreparationToken is not null
          or (
            a.publicationStatus = :pendingStatus
            and a.currentCreateExecutionIntentId is not null
          )
        )
      """)
  boolean existsPreparingOrPendingCreateByPostId(
      @Param("postId") Long postId, @Param("pendingStatus") AnswerPublicationStatus pendingStatus);

  @Query("select a.id from AnswerEntity a where a.postId = :postId order by a.id")
  List<Long> findIdsByPostId(@Param("postId") Long postId);

  @Query(
      value =
          "SELECT a.id FROM answers a "
              + "LEFT JOIN posts p ON p.id = a.post_id "
              + "WHERE p.id IS NULL "
              + "ORDER BY a.id "
              + "LIMIT :batchSize",
      nativeQuery = true)
  List<Long> findOrphanAnswerIds(@Param("batchSize") int batchSize);

  void deleteAllByPostId(Long postId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update AnswerEntity a
      set a.publicationStatus = :pendingStatus,
          a.currentCreateExecutionIntentId = :executionIntentId,
          a.createPreparationToken = null,
          a.createPreparationExpiresAt = null,
          a.publicationFailureTerminalStatus = null,
          a.publicationFailureReason = null
      where a.id = :answerId
        and a.publicationStatus = :pendingStatus
        and a.createPreparationToken = :preparationToken
        and a.currentCreateExecutionIntentId is null
      """)
  int bindCreateIntentIfCurrent(
      @Param("answerId") Long answerId,
      @Param("preparationToken") String preparationToken,
      @Param("executionIntentId") String executionIntentId,
      @Param("pendingStatus") AnswerPublicationStatus pendingStatus);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update AnswerEntity a
      set a.publicationStatus = :visibleStatus,
          a.currentCreateExecutionIntentId = null,
          a.createPreparationToken = null,
          a.createPreparationExpiresAt = null,
          a.publicationFailureTerminalStatus = null,
          a.publicationFailureReason = null,
          a.reconciliationRequiredReason = null,
          a.reconciliationRequiredIntentId = null
      where a.id = :answerId
        and a.publicationStatus = :pendingStatus
        and a.currentCreateExecutionIntentId = :executionIntentId
      """)
  int confirmCreateIfCurrent(
      @Param("answerId") Long answerId,
      @Param("executionIntentId") String executionIntentId,
      @Param("pendingStatus") AnswerPublicationStatus pendingStatus,
      @Param("visibleStatus") AnswerPublicationStatus visibleStatus);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update AnswerEntity a
      set a.publicationStatus = :failedStatus,
          a.currentCreateExecutionIntentId = null,
          a.createPreparationToken = null,
          a.createPreparationExpiresAt = null,
          a.publicationFailureTerminalStatus = :terminalStatus,
          a.publicationFailureReason = :failureReason
      where a.id = :answerId
        and a.publicationStatus = :pendingStatus
        and a.currentCreateExecutionIntentId = :executionIntentId
      """)
  int markCreateFailedIfCurrent(
      @Param("answerId") Long answerId,
      @Param("executionIntentId") String executionIntentId,
      @Param("terminalStatus") String terminalStatus,
      @Param("failureReason") String failureReason,
      @Param("pendingStatus") AnswerPublicationStatus pendingStatus,
      @Param("failedStatus") AnswerPublicationStatus failedStatus);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update AnswerEntity a
      set a.pendingDeleteStatus = :pendingDeleteStatus,
          a.currentDeleteExecutionIntentId = :executionIntentId,
          a.deletePreparationToken = null,
          a.deletePreparationExpiresAt = null,
          a.deleteFailureTerminalStatus = null,
          a.deleteFailureReason = null
      where a.id = :answerId
        and a.publicationStatus = :visibleStatus
        and a.pendingDeleteStatus = :preparingDeleteStatus
        and a.deletePreparationToken = :preparationToken
        and a.currentDeleteExecutionIntentId is null
      """)
  int bindDeleteIntentIfCurrent(
      @Param("answerId") Long answerId,
      @Param("preparationToken") String preparationToken,
      @Param("executionIntentId") String executionIntentId,
      @Param("visibleStatus") AnswerPublicationStatus visibleStatus,
      @Param("preparingDeleteStatus") AnswerDeleteStatus preparingDeleteStatus,
      @Param("pendingDeleteStatus") AnswerDeleteStatus pendingDeleteStatus);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update AnswerEntity a
      set a.pendingDeleteStatus = null,
          a.currentDeleteExecutionIntentId = null,
          a.deletePreparationToken = null,
          a.deletePreparationExpiresAt = null,
          a.deleteFailureTerminalStatus = :terminalStatus,
          a.deleteFailureReason = :failureReason
      where a.id = :answerId
        and a.currentDeleteExecutionIntentId = :executionIntentId
      """)
  int rollbackDeleteIfCurrent(
      @Param("answerId") Long answerId,
      @Param("executionIntentId") String executionIntentId,
      @Param("terminalStatus") String terminalStatus,
      @Param("failureReason") String failureReason);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update AnswerEntity a
      set a.pendingDeleteStatus = null,
          a.currentDeleteExecutionIntentId = null,
          a.deletePreparationToken = null,
          a.deletePreparationExpiresAt = null,
          a.deleteFailureTerminalStatus = :terminalStatus,
          a.deleteFailureReason = :failureReason
      where a.id = :answerId
        and a.publicationStatus = :visibleStatus
        and a.pendingDeleteStatus = :preparingDeleteStatus
        and a.deletePreparationToken = :preparationToken
        and a.currentDeleteExecutionIntentId is null
      """)
  int rollbackDeletePreparationIfCurrent(
      @Param("answerId") Long answerId,
      @Param("preparationToken") String preparationToken,
      @Param("terminalStatus") String terminalStatus,
      @Param("failureReason") String failureReason,
      @Param("visibleStatus") AnswerPublicationStatus visibleStatus,
      @Param("preparingDeleteStatus") AnswerDeleteStatus preparingDeleteStatus);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update AnswerEntity a
      set a.publicationStatus = :reconciliationStatus,
          a.reconciliationRequiredReason = :conflictReason,
          a.reconciliationRequiredIntentId = :executionIntentId
      where a.id = :answerId
        and (a.currentDeleteExecutionIntentId is null or a.currentDeleteExecutionIntentId <> :executionIntentId)
      """)
  int markDeleteSyncConflictIfMismatched(
      @Param("answerId") Long answerId,
      @Param("executionIntentId") String executionIntentId,
      @Param("conflictReason") String conflictReason,
      @Param("reconciliationStatus") AnswerPublicationStatus reconciliationStatus);

  interface PostAnswerCount {
    Long getPostId();

    Long getAnswerCount();
  }
}
