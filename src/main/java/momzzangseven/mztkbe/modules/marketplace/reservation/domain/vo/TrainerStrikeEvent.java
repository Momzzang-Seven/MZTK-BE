package momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo;

/**
 * Domain event emitted by the reservation module when a trainer incurs a strike penalty.
 *
 * <p>This is a plain record (no Spring or framework dependencies) that carries just enough
 * information for the event listener to delegate to the sanction module via its input port.
 *
 * <p>Lifecycle: Published by reservation application services after terminal trainer-fault
 * transitions and consumed by an outer adapter.
 *
 * <p>Reasons:
 *
 * <ul>
 *   <li>{@link #REASON_REJECT} — trainer explicitly rejected a pending reservation
 *   <li>{@link #REASON_TIMEOUT} — reservation was auto-cancelled by the scheduler due to inactivity
 * </ul>
 *
 * @param trainerId the ID of the trainer who receives the strike
 * @param reason the reason for the strike; use the provided constants ({@link #REASON_REJECT},
 *     {@link #REASON_TIMEOUT})
 */
public record TrainerStrikeEvent(Long trainerId, String reason) {

  /** Strike reason: the trainer explicitly rejected a pending reservation. */
  public static final String REASON_REJECT = "REJECT";

  /** Strike reason: the scheduler auto-cancelled the reservation due to trainer inactivity. */
  public static final String REASON_TIMEOUT = "TIMEOUT";
}
