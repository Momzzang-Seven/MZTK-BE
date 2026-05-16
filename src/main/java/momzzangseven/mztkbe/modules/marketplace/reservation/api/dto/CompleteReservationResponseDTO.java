package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CompleteReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

public record CompleteReservationResponseDTO(
    Long reservationId,
    ReservationStatus status,
    String escrowStatus,
    ReservationWeb3WriteResponseDTO web3) {
  public static CompleteReservationResponseDTO from(CompleteReservationResult result) {
    return new CompleteReservationResponseDTO(
        result.reservationId(),
        result.status(),
        result.escrowStatus(),
        ReservationWeb3WriteResponseDTO.from(result.web3()));
  }
}
