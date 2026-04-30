package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CreateReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

public record CreateReservationResponseDTO(Long reservationId, ReservationStatus status) {
  public static CreateReservationResponseDTO from(CreateReservationResult result) {
    return new CreateReservationResponseDTO(result.reservationId(), result.status());
  }
}
