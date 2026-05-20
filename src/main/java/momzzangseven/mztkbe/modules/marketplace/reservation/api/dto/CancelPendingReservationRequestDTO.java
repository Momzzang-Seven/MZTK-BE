package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CancelPendingReservationCommand;

/** Path DTO for cancelling a pending reservation. */
public record CancelPendingReservationRequestDTO(Long reservationId) {

  public CancelPendingReservationCommand toCommand(Long userId) {
    return new CancelPendingReservationCommand(reservationId, userId);
  }
}
