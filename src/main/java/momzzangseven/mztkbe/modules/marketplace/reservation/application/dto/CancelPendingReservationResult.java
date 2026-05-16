package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/** Result returned after a user cancels a pending reservation. */
public record CancelPendingReservationResult(
    Long reservationId,
    ReservationStatus status,
    String escrowStatus,
    ReservationExecutionWriteView web3) {

  public CancelPendingReservationResult(Long reservationId, ReservationStatus status) {
    this(reservationId, status, null, null);
  }
}
