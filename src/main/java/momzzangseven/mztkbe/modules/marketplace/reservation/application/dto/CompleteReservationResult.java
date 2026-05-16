package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/** Result returned after a user completes a reservation. */
public record CompleteReservationResult(
    Long reservationId,
    ReservationStatus status,
    String escrowStatus,
    ReservationExecutionWriteView web3) {

  public CompleteReservationResult(Long reservationId, ReservationStatus status) {
    this(reservationId, status, null, null);
  }
}
