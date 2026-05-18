package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CancelPendingReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationDisplayStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

public record CancelPendingReservationResponseDTO(
    Long reservationId,
    ReservationDisplayStatus status,
    ReservationStatus businessStatus,
    String escrowStatus,
    ReservationWeb3WriteResponseDTO web3) {
  public static CancelPendingReservationResponseDTO from(CancelPendingReservationResult result) {
    return new CancelPendingReservationResponseDTO(
        result.reservationId(),
        result.status(),
        result.businessStatus(),
        result.escrowStatus(),
        ReservationWeb3WriteResponseDTO.from(result.web3()));
  }
}
