package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * Output port for reading reservation data from persistence.
 *
 * <p>Provides targeted query methods aligned to each service's needs to avoid over-fetching.
 */
public interface LoadReservationPort {

  /** Load a single reservation by its primary key. */
  Optional<Reservation> findById(Long reservationId);

  /** Load a single reservation by its primary key with a pessimistic write lock. */
  Optional<Reservation> findByIdWithLock(Long reservationId);

  /**
   * Count active (PENDING or APPROVED) reservations for a single slot.
   *
   * <p>Used by the auto-cancel scheduler and slot-capacity checks.
   */
  int countActiveReservationsBySlotId(Long slotId);

  java.util.Map<Long, Integer> countActiveReservationsBySlotIds(java.util.List<Long> slotIds);

  int countActiveReservationsBySlotIdAndDate(Long slotId, java.time.LocalDate date);

  /**
   * Count active reservations for a slot with a pessimistic write lock (SELECT ... FOR UPDATE).
   *
   * <p>Must be called within an active transaction. Used by {@code CreateReservationService} to
   * prevent over-commit under concurrent reservation requests.
   *
   * @param slotId target slot ID
   * @return active reservation count (PENDING + APPROVED)
   */
  int countActiveReservationsBySlotIdAndDateWithLock(Long slotId, java.time.LocalDate date);

  /**
   * Count active reservations grouped by slot ID for a list of slots in a single query.
   *
   * <p>Used by {@code GetClassReservationInfoService} to populate remaining capacity.
   */
  Map<java.time.LocalDate, Integer> countActiveReservationsBySlotIdAndDateRange(
      Long slotId, java.time.LocalDate startDate, java.time.LocalDate endDate);

  /**
   * Fetch PENDING reservations eligible for auto-cancellation.
   *
   * @param nowMinusTimeout created_at cutoff (now - 72 h)
   * @param nowPlusWindow session start cutoff (now + 1 h)
   * @param batchSize maximum rows to fetch per batch cycle
   */
  List<Reservation> findPendingForAutoCancel(
      LocalDateTime nowMinusTimeout, LocalDateTime nowPlusWindow, int batchSize);

  /**
   * Fetch APPROVED reservations eligible for auto-settlement (class ended &gt; 24 h ago).
   *
   * @param now current server time
   * @param batchSize maximum rows to fetch per batch cycle
   */
  List<Reservation> findApprovedForAutoSettle(LocalDateTime now, int batchSize);

  /** Returns true if the slot has any reservation (active or historical). */
  boolean hasAnyReservationHistory(Long slotId);

  /**
   * Fetch all reservations belonging to the given user, ordered by reservation_date DESC.
   *
   * @param userId the user's ID
   * @param status optional status filter; if null, all statuses are returned
   * @return list of matching reservations
   */
  List<Reservation> findByUserId(Long userId, ReservationStatus status);

  /**
   * Fetch all reservations assigned to the given trainer, ordered by reservation_date DESC.
   *
   * @param trainerId the trainer's ID
   * @param status optional status filter; if null, all statuses are returned
   * @return list of matching reservations
   */
  List<Reservation> findByTrainerId(Long trainerId, ReservationStatus status);
}
