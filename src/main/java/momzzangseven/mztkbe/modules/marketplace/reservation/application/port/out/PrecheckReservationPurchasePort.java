package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrecheckReservationPurchaseCommand;

/** Reservation-owned output port for Web3-facing purchase prechecks. */
public interface PrecheckReservationPurchasePort {

  void precheckPurchase(PrecheckReservationPurchaseCommand command);
}
