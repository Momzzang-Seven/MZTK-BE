package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationJpaRepository extends JpaRepository<ReservationEntity, Long> {

  // NOTE: Status literals 'PENDING' / 'APPROVED' must match ReservationStatus enum names exactly.
  // If either constant is renamed, update this query accordingly.
  @Query(
      "SELECT COUNT(r) FROM ReservationEntity r WHERE r.slotId = :slotId AND r.status IN ('PENDING', 'APPROVED')")
  int countActiveBySlotId(@Param("slotId") Long slotId);

  /**
   * Same as {@link #countActiveBySlotId} but acquires a pessimistic write lock on matched rows.
   *
   * <p>Prevents concurrent INSERT race conditions during reservation creation. Must be called
   * within an active transaction.
   *
   * @param slotId target slot ID
   * @return active reservation count (PENDING + APPROVED)
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  // NOTE: Keep 'PENDING' / 'APPROVED' literals in sync with ReservationStatus enum.
  @Query(
      "SELECT COUNT(r) FROM ReservationEntity r WHERE r.slotId = :slotId AND r.status IN ('PENDING', 'APPROVED')")
  int countActiveBySlotIdWithLock(@Param("slotId") Long slotId);

  // NOTE: Keep 'PENDING' / 'APPROVED' literals in sync with ReservationStatus enum.
  @Query(
      "SELECT r.slotId, COUNT(r) FROM ReservationEntity r WHERE r.slotId IN :slotIds AND r.status IN ('PENDING', 'APPROVED') GROUP BY r.slotId")
  List<Object[]> countActiveBySlotIdIn(@Param("slotIds") List<Long> slotIds);

  /**
   * Fetch PENDING reservations eligible for auto-cancellation.
   *
   * <p>Condition: created_at older than timeout cutoff OR session starts within 1 h window.
   *
   * <p>Uses native MySQL syntax ({@code TIMESTAMP(date, time)}) instead of JPQL {@code FUNCTION()}
   * to ensure compatibility across test (H2) and production (MySQL) environments when using the
   * MySQL dialect.
   *
   * @param nowMinusTimeout created_at cutoff (now - 72 h)
   * @param nowPlusWindow session start cutoff (now + 1 h)
   * @return list of candidate entities
   */
  @Query(
      value =
          "SELECT * FROM class_reservations "
              + "WHERE status = 'PENDING' "
              + "AND (created_at < :nowMinusTimeout "
              + "  OR TIMESTAMP(reservation_date, reservation_time) < :nowPlusWindow) "
              + "ORDER BY id ASC",
      nativeQuery = true)
  List<ReservationEntity> findPendingForAutoCancel(
      @Param("nowMinusTimeout") LocalDateTime nowMinusTimeout,
      @Param("nowPlusWindow") LocalDateTime nowPlusWindow);

  /**
   * Fetch APPROVED reservations eligible for auto-settlement (class ended &gt; 24 h ago).
   *
   * <p>Uses native MySQL {@code TIMESTAMPADD} and {@code TIMESTAMP} functions. The session end time
   * is computed as {@code TIMESTAMP(date, time) + durationMinutes}, and a further 1440 minutes (24
   * h) is added for the auto-settle window.
   *
   * @param now current timestamp
   * @return list of candidate entities
   */
  @Query(
      value =
          "SELECT * FROM class_reservations "
              + "WHERE status = 'APPROVED' "
              + "AND TIMESTAMPADD(MINUTE, duration_minutes + 1440, "
              + "    TIMESTAMP(reservation_date, reservation_time)) < :now "
              + "ORDER BY id ASC",
      nativeQuery = true)
  List<ReservationEntity> findApprovedForAutoSettle(@Param("now") LocalDateTime now);

  boolean existsBySlotId(Long slotId);

  /**
   * Fetch reservations for a specific user, ordered by reservation_date DESC.
   *
   * <p>If {@code status} is null all statuses are returned; otherwise only the matching status.
   */
  @Query(
      "SELECT r FROM ReservationEntity r "
          + "WHERE r.userId = :userId "
          + "AND (:status IS NULL OR r.status = :status) "
          + "ORDER BY r.reservationDate DESC")
  List<ReservationEntity> findByUserId(
      @Param("userId") Long userId, @Param("status") ReservationStatus status);

  /**
   * Fetch reservations assigned to a specific trainer, ordered by reservation_date DESC.
   *
   * <p>If {@code status} is null all statuses are returned; otherwise only the matching status.
   */
  @Query(
      "SELECT r FROM ReservationEntity r "
          + "WHERE r.trainerId = :trainerId "
          + "AND (:status IS NULL OR r.status = :status) "
          + "ORDER BY r.reservationDate DESC")
  List<ReservationEntity> findByTrainerId(
      @Param("trainerId") Long trainerId, @Param("status") ReservationStatus status);
}
