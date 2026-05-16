package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverReservationEscrowResult;

/** Input port for marketplace reservation Web3 recovery. */
public interface RecoverReservationEscrowUseCase {

  RecoverReservationEscrowResult execute(RecoverReservationEscrowCommand command);
}
