package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * Result returned after a reservation is successfully created.
 *
 * @param reservationId the newly created reservation's primary key
 * @param status initial status (always {@code PENDING})
 */
public record CreateReservationResult(
    Long reservationId,
    ReservationStatus status,
    String escrowStatus,
    String orderKey,
    ReservationExecutionWriteView web3) {

  public CreateReservationResult(Long reservationId, ReservationStatus status) {
    this(reservationId, status, null, null, null);
  }
}
