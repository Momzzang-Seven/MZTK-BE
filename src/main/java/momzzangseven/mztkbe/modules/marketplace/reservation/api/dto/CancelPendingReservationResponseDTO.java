package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CancelPendingReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

public record CancelPendingReservationResponseDTO(Long reservationId, ReservationStatus status) {
  public static CancelPendingReservationResponseDTO from(CancelPendingReservationResult result) {
    return new CancelPendingReservationResponseDTO(result.reservationId(), result.status());
  }
}
