package momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.qna.domain.vo.QnaQuestionUpdateStateStatus;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.persistence.entity.QnaQuestionUpdateStateEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QnaQuestionUpdateStateJpaRepository
    extends JpaRepository<QnaQuestionUpdateStateEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from QnaQuestionUpdateStateEntity s"
          + " where s.postId = :postId"
          + " order by s.updateVersion desc")
  List<QnaQuestionUpdateStateEntity> findLatestByPostIdForUpdate(
      @Param("postId") Long postId, Pageable pageable);

  @Query(
      "select s from QnaQuestionUpdateStateEntity s"
          + " where s.postId = :postId"
          + " order by s.updateVersion desc")
  List<QnaQuestionUpdateStateEntity> findLatestByPostId(
      @Param("postId") Long postId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from QnaQuestionUpdateStateEntity s"
          + " where s.executionIntentPublicId = :publicId")
  Optional<QnaQuestionUpdateStateEntity> findByExecutionIntentPublicIdForUpdate(
      @Param("publicId") String publicId);

  @Query(
      "select s from QnaQuestionUpdateStateEntity s, Web3ExecutionIntentEntity e"
          + " where s.status = :status"
          + " and s.executionIntentPublicId is not null"
          + " and e.publicId = s.executionIntentPublicId"
          + " and e.status = :intentStatus"
          + " and e.actionType = :actionType"
          + " order by s.updatedAt asc, s.id asc")
  List<QnaQuestionUpdateStateEntity> findConfirmedIntentBoundForReconciliation(
      @Param("status") QnaQuestionUpdateStateStatus status,
      @Param("intentStatus") ExecutionIntentStatus intentStatus,
      @Param("actionType") ExecutionActionType actionType,
      Pageable pageable);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update QnaQuestionUpdateStateEntity s"
          + " set s.status = :staleStatus, s.updatedAt = :now"
          + " where s.postId = :postId and s.status in :statuses")
  int markNonTerminalStaleByPostId(
      @Param("postId") Long postId,
      @Param("statuses") Collection<QnaQuestionUpdateStateStatus> statuses,
      @Param("staleStatus") QnaQuestionUpdateStateStatus staleStatus,
      @Param("now") LocalDateTime now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update QnaQuestionUpdateStateEntity s"
          + " set s.executionIntentPublicId = :publicId,"
          + " s.status = :boundStatus,"
          + " s.lastErrorCode = null,"
          + " s.lastErrorReason = null,"
          + " s.updatedAt = :now"
          + " where s.postId = :postId"
          + " and s.updateVersion = :updateVersion"
          + " and s.updateToken = :updateToken"
          + " and s.status in :bindableStatuses"
          + " and (s.executionIntentPublicId is null"
          + "      or s.executionIntentPublicId = :publicId"
          + "      or s.status = :preparationFailedStatus)")
  int bindExecutionIntent(
      @Param("postId") Long postId,
      @Param("updateVersion") Long updateVersion,
      @Param("updateToken") String updateToken,
      @Param("publicId") String publicId,
      @Param("bindableStatuses") Collection<QnaQuestionUpdateStateStatus> bindableStatuses,
      @Param("preparationFailedStatus") QnaQuestionUpdateStateStatus preparationFailedStatus,
      @Param("boundStatus") QnaQuestionUpdateStateStatus boundStatus,
      @Param("now") LocalDateTime now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update QnaQuestionUpdateStateEntity s"
          + " set s.status = :failedStatus,"
          + " s.preparationRetryable = :retryable,"
          + " s.lastErrorCode = :errorCode,"
          + " s.lastErrorReason = :errorReason,"
          + " s.updatedAt = :now"
          + " where s.postId = :postId"
          + " and s.updateVersion = :updateVersion"
          + " and s.updateToken = :updateToken"
          + " and s.status in :retryableStatuses")
  int markPreparationFailed(
      @Param("postId") Long postId,
      @Param("updateVersion") Long updateVersion,
      @Param("updateToken") String updateToken,
      @Param("errorCode") String errorCode,
      @Param("errorReason") String errorReason,
      @Param("retryable") boolean retryable,
      @Param("retryableStatuses") Collection<QnaQuestionUpdateStateStatus> retryableStatuses,
      @Param("failedStatus") QnaQuestionUpdateStateStatus failedStatus,
      @Param("now") LocalDateTime now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update QnaQuestionUpdateStateEntity s"
          + " set s.status = :failedStatus,"
          + " s.preparationRetryable = :retryable,"
          + " s.lastErrorCode = :errorCode,"
          + " s.lastErrorReason = :errorReason,"
          + " s.updatedAt = :now"
          + " where s.executionIntentPublicId = :publicId and s.status in :retryableStatuses")
  int markPreparationFailedByExecutionIntentPublicId(
      @Param("publicId") String publicId,
      @Param("errorCode") String errorCode,
      @Param("errorReason") String errorReason,
      @Param("retryable") boolean retryable,
      @Param("retryableStatuses") Collection<QnaQuestionUpdateStateStatus> retryableStatuses,
      @Param("failedStatus") QnaQuestionUpdateStateStatus failedStatus,
      @Param("now") LocalDateTime now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update QnaQuestionUpdateStateEntity s"
          + " set s.lastErrorCode = :errorCode,"
          + " s.lastErrorReason = :errorReason,"
          + " s.updatedAt = :now"
          + " where s.executionIntentPublicId = :publicId")
  int recordSyncFailure(
      @Param("publicId") String publicId,
      @Param("errorCode") String errorCode,
      @Param("errorReason") String errorReason,
      @Param("now") LocalDateTime now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update QnaQuestionUpdateStateEntity s"
          + " set s.status = :confirmedStatus,"
          + " s.lastErrorCode = null,"
          + " s.lastErrorReason = null,"
          + " s.updatedAt = :now"
          + " where s.executionIntentPublicId = :publicId and s.status = :boundStatus")
  int markConfirmed(
      @Param("publicId") String publicId,
      @Param("boundStatus") QnaQuestionUpdateStateStatus boundStatus,
      @Param("confirmedStatus") QnaQuestionUpdateStateStatus confirmedStatus,
      @Param("now") LocalDateTime now);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update QnaQuestionUpdateStateEntity s"
          + " set s.status = :staleStatus, s.updatedAt = :now"
          + " where s.executionIntentPublicId = :publicId and s.status in :statuses")
  int markStaleByExecutionIntentPublicId(
      @Param("publicId") String publicId,
      @Param("statuses") Collection<QnaQuestionUpdateStateStatus> statuses,
      @Param("staleStatus") QnaQuestionUpdateStateStatus staleStatus,
      @Param("now") LocalDateTime now);
}
