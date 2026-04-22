package momzzangseven.mztkbe.modules.marketplace.application.dto;

/**
 * Command for recording a trainer strike due to a reservation rejection or timeout.
 *
 * @param trainerId the ID of the trainer receiving the strike
 * @param reason human-readable reason code (e.g. "REJECT", "TIMEOUT")
 */
public record RecordTrainerStrikeCommand(Long trainerId, String reason) {}
