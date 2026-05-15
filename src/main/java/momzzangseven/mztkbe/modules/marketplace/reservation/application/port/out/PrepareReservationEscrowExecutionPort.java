package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;

/** Output port for preparing marketplace Web3 execution payloads for reservation user actions. */
public interface PrepareReservationEscrowExecutionPort {

  PrepareReservationEscrowResult preparePurchase(PrepareReservationEscrowCommand command);

  PrepareReservationEscrowResult prepareCancel(PrepareReservationEscrowCommand command);

  PrepareReservationEscrowResult prepareConfirm(PrepareReservationEscrowCommand command);
}
