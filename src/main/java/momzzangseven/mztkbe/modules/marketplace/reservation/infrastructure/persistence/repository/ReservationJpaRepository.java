package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationJpaRepository extends JpaRepository<ReservationEntity, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM ReservationEntity r WHERE r.id = :id")
  java.util.Optional<ReservationEntity> findByIdWithLock(@Param("id") Long id);

  // NOTE: Status literals 'PENDING' / 'APPROVED' must match ReservationStatus enum names exactly.
  // If either constant is renamed, update this query accordingly.
  @Query(
      "SELECT COUNT(r) FROM ReservationEntity r WHERE r.slotId = :slotId AND r.reservationDate = :reservationDate AND r.status IN ('PENDING', 'APPROVED')")
  int countActiveBySlotIdAndDate(
      @Param("slotId") Long slotId, @Param("reservationDate") LocalDate reservationDate);

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
  @Query(
      "SELECT COUNT(r) FROM ReservationEntity r WHERE r.slotId = :slotId AND r.reservationDate = :reservationDate AND r.status IN ('PENDING', 'APPROVED')")
  int countActiveBySlotIdAndDateWithLock(
      @Param("slotId") Long slotId, @Param("reservationDate") LocalDate reservationDate);

  // NOTE: Keep 'PENDING' / 'APPROVED' literals in sync with ReservationStatus enum.
  @Query(
      "SELECT r.reservationDate, COUNT(r) FROM ReservationEntity r WHERE r.slotId = :slotId AND r.reservationDate >= :startDate AND r.reservationDate < :endDate AND r.status IN ('PENDING', 'APPROVED') GROUP BY r.reservationDate")
  List<Object[]> countActiveBySlotIdAndDateRange(
      @Param("slotId") Long slotId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);

  /**
   * Fetch PENDING reservations eligible for auto-cancellation.
   *
   * <p>Condition: created_at older than timeout cutoff OR session starts within 1 h window.
   *
   * <p>Uses standard JPQL instead of native SQL for portability across PostgreSQL and H2.
   *
   * @param nowMinusTimeout created_at cutoff (now - 72 h)
   * @param windowDate session start cutoff date
   * @param windowTime session start cutoff time
   * @param pageable limit
   * @return list of candidate entities
   */
  @Query(
      "SELECT r FROM ReservationEntity r "
          + "WHERE r.status = 'PENDING' "
          + "AND (r.createdAt < :nowMinusTimeout "
          + "  OR r.reservationDate < :windowDate "
          + "  OR (r.reservationDate = :windowDate AND r.reservationTime < :windowTime)) "
          + "ORDER BY r.id ASC")
  List<ReservationEntity> findPendingForAutoCancel(
      @Param("nowMinusTimeout") LocalDateTime nowMinusTimeout,
      @Param("windowDate") LocalDate windowDate,
      @Param("windowTime") LocalTime windowTime,
      org.springframework.data.domain.Pageable pageable);

  /**
   * Fetch APPROVED reservations eligible for auto-settlement (class ended &gt; 24 h ago).
   *
   * <p>Uses standard JPQL to fetch candidates with a rough date filter, leaving exact calculation
   * to Java stream filtering for database compatibility across H2 and PostgreSQL.
   *
   * @param maxDate target cutoff date
   * @param pageable limit
   * @return list of candidate entities
   */
  @Query(
      "SELECT r FROM ReservationEntity r "
          + "WHERE r.status = 'APPROVED' "
          + "AND r.reservationDate <= :maxDate "
          + "ORDER BY r.id ASC")
  List<ReservationEntity> findApprovedCandidates(
      @Param("maxDate") LocalDate maxDate, org.springframework.data.domain.Pageable pageable);

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
