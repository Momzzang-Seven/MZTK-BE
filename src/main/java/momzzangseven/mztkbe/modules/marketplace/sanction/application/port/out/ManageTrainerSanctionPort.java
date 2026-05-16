package momzzangseven.mztkbe.modules.marketplace.sanction.application.port.out;

/**
 * Output port for writing trainer strike records (WRITE-only).
 *
 * <p>Implemented by {@code SanctionManageAdapter} in {@code infrastructure/external/sanction/}.
 */
public interface ManageTrainerSanctionPort {

  /**
   * Record a strike for a trainer.
   *
   * <p>If the resulting strike count reaches or exceeds 3 (or the next multiple of 3), the sanction
   * module will activate a 7-day suspension.
   *
   * @param trainerId trainer's user ID
   * @param reason strike reason ("REJECT" or "TIMEOUT")
   * @return result indicating updated strike count and whether a suspension was triggered
   */
  default RecordStrikeResult recordStrike(Long trainerId, String reason) {
    return recordStrike(trainerId, reason, null, null);
  }

  /**
   * Record a strike with an optional idempotency source.
   *
   * <p>When {@code sourceType/sourceId} are present, repeated calls for the same source must not
   * increment the trainer's strike count. This is required for confirmed hook replay.
   */
  RecordStrikeResult recordStrike(
      Long trainerId, String reason, String sourceType, String sourceId);

  /**
   * Result of a {@link #recordStrike} call.
   *
   * @param strikeCount cumulative total strike count after this increment
   * @param isBanned true if a new suspension was activated
   */
  record RecordStrikeResult(int strikeCount, boolean isBanned) {}
}
