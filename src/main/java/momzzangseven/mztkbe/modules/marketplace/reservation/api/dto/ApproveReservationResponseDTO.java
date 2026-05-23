package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ApproveReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationDisplayStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

public record ApproveReservationResponseDTO(
    Long reservationId, ReservationDisplayStatus status, ReservationStatus businessStatus) {
  public static ApproveReservationResponseDTO from(ApproveReservationResult result) {
    return new ApproveReservationResponseDTO(
        result.reservationId(), result.status(), result.businessStatus());
  }
}
