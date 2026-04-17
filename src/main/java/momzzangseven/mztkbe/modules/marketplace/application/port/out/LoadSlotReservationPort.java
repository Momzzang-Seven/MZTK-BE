package momzzangseven.mztkbe.modules.marketplace.application.port.out;

/**
 * Output port for querying reservation state related to class slots.
 *
 * <p>Used by {@link
 * momzzangseven.mztkbe.modules.marketplace.application.service.UpdateClassService} to enforce two
 * safety constraints when synchronising slots:
 *
 * <ol>
 *   <li>A slot with active (non-refunded, non-cancelled) reservations must not be deleted.
 *   <li>Reducing a slot's capacity below the current active-reservation count is forbidden.
 * </ol>
 *
 * <p>Implemented by the reservation module's infrastructure adapter. Until the reservation module
 * is available, a stub implementation returns safe defaults (0 reservations, false).
 */
public interface LoadSlotReservationPort {

  /**
   * Returns the number of active (non-cancelled, non-refunded) reservations for a given slot.
   *
   * @param slotId the slot ID to query
   * @return active reservation count (≥ 0)
   */
  int countActiveReservations(Long slotId);

  /**
   * Returns true if the given slot has at least one active reservation.
   *
   * <p>Prefer this over {@link #countActiveReservations} when only existence matters, as the
   * underlying implementation may short-circuit after finding the first match.
   *
   * @param slotId the slot ID to check
   * @return true if any active reservation exists
   */
  boolean hasActiveReservation(Long slotId);
}
