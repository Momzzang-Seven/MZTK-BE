package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/** Result returned after a trainer rejects a reservation. */
public record RejectReservationResult(
    Long reservationId,
    ReservationStatus status,
    String escrowStatus,
    ReservationExecutionWriteView web3) {

  public RejectReservationResult(Long reservationId, ReservationStatus status) {
    this(reservationId, status, null, null);
  }
}
