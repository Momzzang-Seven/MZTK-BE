package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.ReservationCreateIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationCreateIdempotencyJpaRepository
    extends JpaRepository<ReservationCreateIdempotencyEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT k FROM ReservationCreateIdempotencyEntity k "
          + "WHERE k.buyerId = :buyerId AND k.keyHash = :keyHash")
  Optional<ReservationCreateIdempotencyEntity> findByBuyerIdAndKeyHashWithLock(
      @Param("buyerId") Long buyerId, @Param("keyHash") String keyHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT k FROM ReservationCreateIdempotencyEntity k "
          + "WHERE k.reservationId = :reservationId")
  Optional<ReservationCreateIdempotencyEntity> findByReservationIdWithLock(
      @Param("reservationId") Long reservationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT k FROM ReservationCreateIdempotencyEntity k WHERE k.id = :id")
  Optional<ReservationCreateIdempotencyEntity> findByIdWithLock(@Param("id") Long id);

  @Modifying
  @Query(
      value =
          """
          INSERT INTO reservation_create_idempotency_keys
              (buyer_id, key_hash, payload_hash, status, expires_at, created_at, updated_at)
          VALUES
              (:buyerId, :keyHash, :payloadHash, 'PREPARING', :expiresAt, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          ON CONFLICT (buyer_id, key_hash) DO NOTHING
          """,
      nativeQuery = true)
  int insertPreparingIfAbsent(
      @Param("buyerId") Long buyerId,
      @Param("keyHash") String keyHash,
      @Param("payloadHash") String payloadHash,
      @Param("expiresAt") java.time.LocalDateTime expiresAt);

  @Modifying
  @Query(
      """
      UPDATE ReservationCreateIdempotencyEntity k
      SET k.actionStateId = :newActionStateId,
          k.updatedAt = :updatedAt
      WHERE k.id = :id
        AND k.actionStateId = :expectedActionStateId
      """)
  int replaceActionStateIfCurrent(
      @Param("id") Long id,
      @Param("expectedActionStateId") Long expectedActionStateId,
      @Param("newActionStateId") Long newActionStateId,
      @Param("updatedAt") LocalDateTime updatedAt);
}
