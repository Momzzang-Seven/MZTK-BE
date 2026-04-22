package momzzangseven.mztkbe.modules.marketplace.api.dto;

import momzzangseven.mztkbe.modules.marketplace.application.dto.CompleteReservationResult;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.ReservationStatus;

public record CompleteReservationResponseDTO(Long reservationId, ReservationStatus status) {
  public static CompleteReservationResponseDTO from(CompleteReservationResult result) {
    return new CompleteReservationResponseDTO(result.reservationId(), result.status());
  }
}
