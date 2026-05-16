package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CreateReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

public record CreateReservationResponseDTO(
    Long reservationId,
    ReservationStatus status,
    String escrowStatus,
    String orderKey,
    ReservationWeb3WriteResponseDTO web3) {
  public static CreateReservationResponseDTO from(CreateReservationResult result) {
    return new CreateReservationResponseDTO(
        result.reservationId(),
        result.status(),
        result.escrowStatus(),
        result.orderKey(),
        ReservationWeb3WriteResponseDTO.from(result.web3()));
  }
}
