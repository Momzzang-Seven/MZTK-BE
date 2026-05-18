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
}
