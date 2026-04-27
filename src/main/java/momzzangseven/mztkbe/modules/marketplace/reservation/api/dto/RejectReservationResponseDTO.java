package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RejectReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

public record RejectReservationResponseDTO(Long reservationId, ReservationStatus status) {
  public static RejectReservationResponseDTO from(RejectReservationResult result) {
    return new RejectReservationResponseDTO(result.reservationId(), result.status());
  }
}
