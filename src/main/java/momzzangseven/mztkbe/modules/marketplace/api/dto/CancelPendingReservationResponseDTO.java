package momzzangseven.mztkbe.modules.marketplace.api.dto;

import momzzangseven.mztkbe.modules.marketplace.application.dto.CancelPendingReservationResult;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.ReservationStatus;

public record CancelPendingReservationResponseDTO(Long reservationId, ReservationStatus status) {
  public static CancelPendingReservationResponseDTO from(CancelPendingReservationResult result) {
    return new CancelPendingReservationResponseDTO(result.reservationId(), result.status());
  }
}
