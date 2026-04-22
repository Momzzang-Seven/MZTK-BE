package momzzangseven.mztkbe.modules.marketplace.sanction.application.dto;

/**
 * Command for recording a trainer strike due to a reservation rejection or timeout.
 *
 * @param trainerId the ID of the trainer receiving the strike
 * @param reason human-readable reason code; use the {@code REASON_*} constants defined here
 */
public record RecordTrainerStrikeCommand(Long trainerId, String reason) {

  /** Trainer explicitly rejected a pending reservation. */
  public static final String REASON_REJECT = "REJECT";

  /** Reservation was auto-cancelled by the scheduler due to trainer inactivity. */
  public static final String REASON_TIMEOUT = "TIMEOUT";
}
