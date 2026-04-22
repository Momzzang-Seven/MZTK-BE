package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

/**
 * Input port that exposes slot-level reservation counts to other modules.
 *
 * <p>The {@code classes} module uses this port (via {@code SlotReservationAdapter}) to determine
 * how many active reservations exist for a given slot — for example, to show remaining capacity in
 * the class detail view, or to verify that a slot can still be deactivated.
 *
 * <p>Cross-module callers must depend on this interface, not on the output port {@code
 * LoadReservationPort}.
 */
public interface GetSlotReservationInfoUseCase {

  /**
   * Count active (PENDING or APPROVED) reservations for the given slot.
   *
   * @param slotId target slot ID
   * @return count of active reservations
   */
  int countActiveReservations(Long slotId);

  /**
   * Returns true if the slot has any reservation in its history (any status).
   *
   * <p>Used to prevent hard-deletion of slots that were ever booked.
   *
   * @param slotId target slot ID
   * @return true if any historical reservation exists
   */
  boolean hasAnyReservationHistory(Long slotId);
}
