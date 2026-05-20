package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ApproveReservationCommand;

/** Path DTO for trainer approval. */
public record ApproveReservationRequestDTO(Long reservationId) {

  public ApproveReservationCommand toCommand(Long trainerId) {
    return new ApproveReservationCommand(reservationId, trainerId);
  }
}
