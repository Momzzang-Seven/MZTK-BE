package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

/**
 * Output port for recording a trainer strike in the sanction module.
 *
 * <p>Belongs to the {@code reservation} module. The implementation lives in {@code
 * reservation/infrastructure/external/sanction/} and delegates to {@code
 * sanction/application/port/in/RecordTrainerStrikeUseCase}.
 *
 * <p>Reason constants are shared via {@link
 * momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.TrainerStrikeEvent}.
 */
public interface RecordTrainerStrikePort {

  String SOURCE_MARKETPLACE_RESERVATION_REJECT = "MARKETPLACE_RESERVATION_REJECT";

  default void recordStrike(Long trainerId, String reason) {
    recordStrike(trainerId, reason, null, null);
  }

  /**
   * Records a strike for the given trainer.
   *
   * @param trainerId trainer's user ID
   * @param reason strike reason; use {@code TrainerStrikeEvent.REASON_REJECT} or {@code
   *     TrainerStrikeEvent.REASON_TIMEOUT}
   * @param sourceType optional idempotency source type
   * @param sourceId optional idempotency source id
   */
  void recordStrike(Long trainerId, String reason, String sourceType, String sourceId);
}
