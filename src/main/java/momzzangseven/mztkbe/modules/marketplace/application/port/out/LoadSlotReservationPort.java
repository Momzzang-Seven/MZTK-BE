package momzzangseven.mztkbe.modules.marketplace.application.port.out;

/**
 * Output port for querying reservation counts associated with a class slot.
 *
 * <p>Used by {@link
 * momzzangseven.mztkbe.modules.marketplace.application.service.UpdateClassService} to enforce two
 * business rules during slot modification:
 *
 * <ul>
 *   <li><b>Active reservation guard</b>: a slot that has at least one future/active reservation
 *       cannot be deactivated or deleted — {@link #countActiveReservations} is used for this check.
 *   <li><b>Soft vs hard delete</b>: a slot with any historical reservation record must be
 *       soft-deleted ({@code active=false}) to preserve booking history; only slots with zero total
 *       reservations may be hard-deleted — {@link #hasAnyReservationHistory} is used for this.
 * </ul>
 *
 * <p>Until the reservation module is available, {@code SlotReservationAdapter} is a stub that
 * always returns {@code 0} / {@code false}, enabling safe slot hard-deletion for now.
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
