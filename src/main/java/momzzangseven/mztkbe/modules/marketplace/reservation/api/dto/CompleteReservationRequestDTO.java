package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CompleteReservationCommand;

/** Path DTO for completing an approved reservation. */
public record CompleteReservationRequestDTO(Long reservationId) {

  public CompleteReservationCommand toCommand(Long userId) {
    return new CompleteReservationCommand(reservationId, userId);
  }
}
