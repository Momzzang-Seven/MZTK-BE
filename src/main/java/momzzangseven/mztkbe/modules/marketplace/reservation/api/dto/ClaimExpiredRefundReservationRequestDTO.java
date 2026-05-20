package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ClaimExpiredRefundReservationCommand;

/** Path DTO for claiming a deadline-expired refund. */
public record ClaimExpiredRefundReservationRequestDTO(Long reservationId) {

  public ClaimExpiredRefundReservationCommand toCommand(Long userId) {
    return new ClaimExpiredRefundReservationCommand(reservationId, userId);
  }
}
