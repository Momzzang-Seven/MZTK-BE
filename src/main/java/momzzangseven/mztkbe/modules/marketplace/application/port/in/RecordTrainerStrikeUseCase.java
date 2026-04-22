package momzzangseven.mztkbe.modules.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.application.dto.RecordTrainerStrikeCommand;

/**
 * Input port for recording a trainer strike.
 *
 * <p>Called by infrastructure driving adapters (e.g. {@code ReservationSanctionEventListener}) when
 * a trainer-rejection domain event is committed. Separating this into an input port ensures the
 * event listener never bypasses the application layer to reach an output port directly.
 */
public interface RecordTrainerStrikeUseCase {

  /**
   * Records a strike against the specified trainer.
   *
   * @param command contains the trainer ID and reason for the strike
   */
  void execute(RecordTrainerStrikeCommand command);
}
