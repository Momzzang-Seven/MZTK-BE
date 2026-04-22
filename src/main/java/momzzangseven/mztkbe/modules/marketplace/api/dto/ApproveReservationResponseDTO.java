package momzzangseven.mztkbe.modules.marketplace.api.dto;

import momzzangseven.mztkbe.modules.marketplace.application.dto.ApproveReservationResult;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.ReservationStatus;

public record ApproveReservationResponseDTO(Long reservationId, ReservationStatus status) {
  public static ApproveReservationResponseDTO from(ApproveReservationResult result) {
    return new ApproveReservationResponseDTO(result.reservationId(), result.status());
  }
}
