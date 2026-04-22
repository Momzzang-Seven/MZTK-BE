package momzzangseven.mztkbe.modules.marketplace.api.dto;

import momzzangseven.mztkbe.modules.marketplace.application.dto.CreateReservationResult;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.ReservationStatus;

public record CreateReservationResponseDTO(Long reservationId, ReservationStatus status) {
  public static CreateReservationResponseDTO from(CreateReservationResult result) {
    return new CreateReservationResponseDTO(result.reservationId(), result.status());
  }
}
