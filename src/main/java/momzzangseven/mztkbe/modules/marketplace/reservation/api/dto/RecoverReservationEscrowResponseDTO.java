package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

public record RecoverReservationEscrowResponseDTO(
    Long reservationId,
    ReservationStatus status,
    String escrowStatus,
    ReservationWeb3WriteResponseDTO web3) {

  public static RecoverReservationEscrowResponseDTO from(RecoverReservationEscrowResult result) {
    return new RecoverReservationEscrowResponseDTO(
        result.reservationId(),
        result.status(),
        result.escrowStatus(),
        ReservationWeb3WriteResponseDTO.from(result.web3()));
  }
}
