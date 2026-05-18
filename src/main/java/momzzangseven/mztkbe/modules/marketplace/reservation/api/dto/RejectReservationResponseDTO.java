package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RejectReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationDisplayStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

public record RejectReservationResponseDTO(
    Long reservationId,
    ReservationDisplayStatus status,
    ReservationStatus businessStatus,
    String escrowStatus,
    ReservationWeb3WriteResponseDTO web3) {
  public static RejectReservationResponseDTO from(RejectReservationResult result) {
    return new RejectReservationResponseDTO(
        result.reservationId(),
        result.status(),
        result.businessStatus(),
        result.escrowStatus(),
        ReservationWeb3WriteResponseDTO.from(result.web3()));
  }
}
