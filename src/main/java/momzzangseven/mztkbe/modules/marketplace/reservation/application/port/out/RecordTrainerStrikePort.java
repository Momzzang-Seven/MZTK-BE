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

  /**
   * Records a strike for the given trainer.
   *
   * @param trainerId trainer's user ID
   * @param reason strike reason; use {@code TrainerStrikeEvent.REASON_REJECT} or {@code
   *     TrainerStrikeEvent.REASON_TIMEOUT}
   */
  void recordStrike(Long trainerId, String reason);
}
