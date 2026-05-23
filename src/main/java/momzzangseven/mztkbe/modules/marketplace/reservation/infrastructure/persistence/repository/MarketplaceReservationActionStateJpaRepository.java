package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.MarketplaceReservationActionStateEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceReservationActionStateJpaRepository
    extends JpaRepository<MarketplaceReservationActionStateEntity, Long> {

  @Query(
      "SELECT a FROM MarketplaceReservationActionStateEntity a "
          + "WHERE a.reservationId = :reservationId "
          + "ORDER BY a.attemptNo DESC, a.id DESC")
  List<MarketplaceReservationActionStateEntity> findLatestByReservationId(
      @Param("reservationId") Long reservationId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM MarketplaceReservationActionStateEntity a WHERE a.id = :id")
  Optional<MarketplaceReservationActionStateEntity> findByIdWithLock(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT a FROM MarketplaceReservationActionStateEntity a "
          + "WHERE a.reservationId = :reservationId "
          + "ORDER BY a.attemptNo DESC, a.id DESC")
  List<MarketplaceReservationActionStateEntity> findLatestByReservationIdWithLock(
      @Param("reservationId") Long reservationId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT a FROM MarketplaceReservationActionStateEntity a "
          + "WHERE a.executionIntentPublicId = :executionIntentPublicId")
  Optional<MarketplaceReservationActionStateEntity> findByExecutionIntentPublicIdWithLock(
      @Param("executionIntentPublicId") String executionIntentPublicId);

  @Query(
      "SELECT a FROM MarketplaceReservationActionStateEntity a "
          + "WHERE a.reservationId = :reservationId "
          + "AND a.status IN :statuses "
          + "ORDER BY a.attemptNo DESC, a.id DESC")
  List<MarketplaceReservationActionStateEntity> findByReservationIdAndStatusIn(
      @Param("reservationId") Long reservationId, @Param("statuses") Collection<String> statuses);

  @Query(
      "SELECT a FROM MarketplaceReservationActionStateEntity a "
          + "WHERE a.reservationId = :reservationId "
          + "AND a.actionType = :actionType "
          + "ORDER BY a.attemptNo DESC, a.id DESC")
  List<MarketplaceReservationActionStateEntity> findLatestByReservationIdAndActionType(
      @Param("reservationId") Long reservationId,
      @Param("actionType") String actionType,
      Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT a FROM MarketplaceReservationActionStateEntity a "
          + "WHERE a.reservationId = :reservationId "
          + "AND a.actionType = :actionType "
          + "ORDER BY a.attemptNo DESC, a.id DESC")
  List<MarketplaceReservationActionStateEntity> findLatestByReservationIdAndActionTypeWithLock(
      @Param("reservationId") Long reservationId,
      @Param("actionType") String actionType,
      Pageable pageable);

  @Query(
      value =
          """
          SELECT *
          FROM marketplace_reservation_action_states a
          WHERE a.status = 'PREPARING'
            AND a.execution_intent_public_id IS NULL
            AND a.action_type IN ('ADMIN_REFUND', 'ADMIN_SETTLE')
            AND a.preparation_expires_at IS NOT NULL
            AND a.preparation_expires_at <= :now
          ORDER BY a.id ASC
          LIMIT :batchSize
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<MarketplaceReservationActionStateEntity> findExpiredAdminPreparingAttemptsWithLock(
      @Param("now") LocalDateTime now, @Param("batchSize") int batchSize);

  @Query(
      """
      SELECT a FROM MarketplaceReservationActionStateEntity a
      WHERE a.status = 'PREPARING'
        AND a.executionIntentPublicId IS NULL
        AND a.actionType IN ('ADMIN_REFUND', 'ADMIN_SETTLE')
        AND a.preparationExpiresAt IS NOT NULL
        AND a.preparationExpiresAt <= :now
      ORDER BY a.id ASC
      """)
  List<MarketplaceReservationActionStateEntity> findExpiredAdminPreparingAttemptsForInspection(
      @Param("now") LocalDateTime now, Pageable pageable);

  @Query(
      value =
          """
          SELECT a.*
          FROM marketplace_reservation_action_states a
          WHERE a.status = 'INTENT_BOUND'
            AND a.execution_intent_public_id IS NOT NULL
            AND a.action_type IN ('ADMIN_REFUND', 'ADMIN_SETTLE')
            AND EXISTS (
              SELECT 1
              FROM web3_execution_intents i
              LEFT JOIN web3_transactions t ON t.id = i.submitted_tx_id
              WHERE i.public_id = a.execution_intent_public_id
                AND (
                  i.status IN (
                    'CONFIRMED',
                    'FAILED_ONCHAIN',
                    'EXPIRED',
                    'CANCELED',
                    'NONCE_STALE'
                  )
                  OR (
                    i.status IN ('SIGNED', 'PENDING_ONCHAIN')
                    AND t.status IN ('SUCCEEDED', 'FAILED_ONCHAIN')
                  )
                )
              )
          ORDER BY a.updated_at ASC, a.id ASC
          LIMIT :batchSize
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<MarketplaceReservationActionStateEntity> findBoundAdminExecutionAttemptsForTerminalReplay(
      @Param("batchSize") int batchSize);

  @Query(
      "SELECT a.executionIntentPublicId FROM MarketplaceReservationActionStateEntity a "
          + "WHERE a.executionIntentPublicId IN :publicIds "
          + "AND a.status IN :statuses")
  List<String> findExecutionIntentPublicIdsInByStatusIn(
      @Param("publicIds") Collection<String> publicIds,
      @Param("statuses") Collection<String> statuses);

  @Query(
      """
      SELECT COUNT(a)
      FROM MarketplaceReservationActionStateEntity a
      WHERE a.id = :actionStateId
        AND a.reservationId = :reservationId
        AND a.escrowId = :escrowId
        AND a.actionType IN :actionTypes
        AND a.attemptToken = :attemptToken
        AND a.status IN :statuses
      """)
  long countActiveByPayloadEvidence(
      @Param("actionStateId") Long actionStateId,
      @Param("reservationId") Long reservationId,
      @Param("escrowId") Long escrowId,
      @Param("actionTypes") Collection<String> actionTypes,
      @Param("attemptToken") String attemptToken,
      @Param("statuses") Collection<String> statuses);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      UPDATE MarketplaceReservationActionStateEntity a
      SET a.executionIntentPublicId = :executionIntentPublicId,
          a.status = 'INTENT_BOUND',
          a.updatedAt = :updatedAt
      WHERE a.id = :actionStateId
        AND a.attemptToken = :attemptToken
        AND a.status = 'PREPARING'
        AND a.executionIntentPublicId IS NULL
      """)
  int bindExecutionIntent(
      @Param("actionStateId") Long actionStateId,
      @Param("attemptToken") String attemptToken,
      @Param("executionIntentPublicId") String executionIntentPublicId,
      @Param("updatedAt") LocalDateTime updatedAt);

  @Modifying(flushAutomatically = true)
  @Query(
      """
      UPDATE MarketplaceReservationActionStateEntity a
      SET a.status = 'STALE',
          a.retryable = false,
          a.errorCode = 'RETRY_SUPERSEDED',
          a.errorReason = :errorReason,
          a.updatedAt = :updatedAt
      WHERE a.id = :actionStateId
      """)
  int markStaleForRetry(
      @Param("actionStateId") Long actionStateId,
      @Param("errorReason") String errorReason,
      @Param("updatedAt") LocalDateTime updatedAt);
}
