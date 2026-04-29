package momzzangseven.mztkbe.modules.marketplace.classes.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Output port for querying reservation counts associated with a class slot.
 *
 * <p>Used by {@link
 * momzzangseven.mztkbe.modules.marketplace.classes.application.service.UpdateClassService} to
 * enforce two business rules during slot modification:
 *
 * <ul>
 *   <li><b>Active reservation guard</b>: a slot that has at least one future/active reservation
 *       cannot be deactivated or deleted — {@link #countActiveReservations(Long)} is used for this
 *       check.
 *   <li><b>Soft vs hard delete</b>: a slot with any historical reservation record must be
 *       soft-deleted ({@code active=false}) to preserve booking history; only slots with zero total
 *       reservations may be hard-deleted — {@link #hasAnyReservationHistory} is used for this.
 * </ul>
 *
 * <p>Until the reservation module adapter is fully wired, the infrastructure implementation may
 * return stub values ({@code 0} / {@code false}).
 */
public interface LoadSlotReservationPort {

  /**
   * Returns the number of active (future, not cancelled) reservations for the given slot.
   *
   * <p>If this value is greater than zero, modifying or deactivating the slot is forbidden.
   *
   * @param slotId slot ID
   * @return count of active reservations; 0 if none
   */
  int countActiveReservations(Long slotId);

  /**
   * Returns a map of slotId → active reservation count for a batch of slots in a single query.
   *
   * @param slotIds list of slot IDs to query
   * @return map where each key is a slot ID and the value is its active reservation count
   */
  Map<Long, Integer> countActiveReservationsIn(List<Long> slotIds);

  /**
   * Returns the number of active reservations for a slot on a specific date.
   *
   * @param slotId slot ID
   * @param date the session date to check
   * @return count of active reservations on that date
   */
  int countActiveReservations(Long slotId, LocalDate date);

  /**
   * Returns a map of date → active reservation count for a slot over a date range.
   *
   * <p>Used by {@code GetClassReservationInfoService} to populate remaining capacity per day.
   *
   * @param slotId slot ID
   * @param startDate inclusive range start
   * @param endDate inclusive range end
   * @return map where each key is a date and the value is the active reservation count
   */
  Map<LocalDate, Integer> countActiveReservationsForDateRange(
      Long slotId, LocalDate startDate, LocalDate endDate);

  /**
   * Returns true if the slot has any reservation record in history (active or past).
   *
   * <p>A slot with historical reservations must be soft-deleted rather than hard-deleted to
   * preserve booking audit trails.
   *
   * @param slotId slot ID
   * @return true if any reservation row references this slot
   */
  boolean hasAnyReservationHistory(Long slotId);
}
