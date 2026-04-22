package momzzangseven.mztkbe.modules.marketplace.api.dto;

import momzzangseven.mztkbe.modules.marketplace.application.dto.RejectReservationResult;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.ReservationStatus;

public record RejectReservationResponseDTO(Long reservationId, ReservationStatus status) {
  public static RejectReservationResponseDTO from(RejectReservationResult result) {
    return new RejectReservationResponseDTO(result.reservationId(), result.status());
  }
}
