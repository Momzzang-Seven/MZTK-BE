package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CompleteReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

public record CompleteReservationResponseDTO(Long reservationId, ReservationStatus status) {
  public static CompleteReservationResponseDTO from(CompleteReservationResult result) {
    return new CompleteReservationResponseDTO(result.reservationId(), result.status());
  }
}
