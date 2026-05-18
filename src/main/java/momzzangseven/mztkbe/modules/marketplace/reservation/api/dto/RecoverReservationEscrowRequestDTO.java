package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverReservationEscrowCommand;

/** Path DTO for recovering a reservation escrow execution. */
public record RecoverReservationEscrowRequestDTO(Long reservationId) {

  public RecoverReservationEscrowCommand toCommand(Long requesterId) {
    return new RecoverReservationEscrowCommand(reservationId, requesterId);
  }
}
