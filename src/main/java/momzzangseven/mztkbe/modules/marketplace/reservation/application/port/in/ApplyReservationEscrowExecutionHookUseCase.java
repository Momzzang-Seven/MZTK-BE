package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionConfirmedCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowExecutionTerminatedCommand;

public interface ApplyReservationEscrowExecutionHookUseCase {

  void afterExecutionConfirmed(ReservationEscrowExecutionConfirmedCommand command);

  void afterExecutionTerminated(ReservationEscrowExecutionTerminatedCommand command);
}
