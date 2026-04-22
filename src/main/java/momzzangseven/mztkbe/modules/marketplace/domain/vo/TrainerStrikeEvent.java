package momzzangseven.mztkbe.modules.marketplace.domain.vo;

/**
 * Domain event emitted when a trainer incurs a strike penalty.
 *
 * <p>This is a plain record (no Spring or framework dependencies) that carries just enough
 * information for the event listener to delegate to the sanction module via output port.
 *
 * <p>Reasons:
 *
 * <ul>
 *   <li>{@code "REJECT"} — trainer explicitly rejected a pending reservation
 *   <li>{@code "TIMEOUT"} — reservation was auto-cancelled by the scheduler due to inactivity
 * </ul>
 *
 * @param trainerId the ID of the trainer who receives the strike
 * @param reason the reason for the strike ("REJECT" or "TIMEOUT")
 */
public record TrainerStrikeEvent(Long trainerId, String reason) {}
