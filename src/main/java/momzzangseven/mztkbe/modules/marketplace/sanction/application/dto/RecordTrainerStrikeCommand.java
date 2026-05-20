package momzzangseven.mztkbe.modules.marketplace.sanction.application.dto;

/**
 * Command for recording a trainer strike due to a reservation rejection or timeout.
 *
 * @param trainerId the ID of the trainer receiving the strike
 * @param reason human-readable reason code; use the {@code REASON_*} constants defined here
 * @param sourceType optional idempotency source type for replay-safe strike recording
 * @param sourceId optional idempotency source id for replay-safe strike recording
 */
public record RecordTrainerStrikeCommand(
    Long trainerId, String reason, String sourceType, String sourceId) {

  /** Trainer explicitly rejected a pending reservation. */
  public static final String REASON_REJECT = "REJECT";

  /** Reservation was auto-cancelled by the scheduler due to trainer inactivity. */
  public static final String REASON_TIMEOUT = "TIMEOUT";

  /** Marketplace trainer-reject outcome source used to dedupe confirmed hook replay. */
  public static final String SOURCE_MARKETPLACE_RESERVATION_REJECT =
      "MARKETPLACE_RESERVATION_REJECT";

  public RecordTrainerStrikeCommand(Long trainerId, String reason) {
    this(trainerId, reason, null, null);
  }
}
