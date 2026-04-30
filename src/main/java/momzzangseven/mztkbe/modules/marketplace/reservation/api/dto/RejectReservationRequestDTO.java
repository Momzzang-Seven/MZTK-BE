package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RejectReservationCommand;

public record RejectReservationRequestDTO(@NotBlank @Size(max = 200) String rejectionReason) {
  public RejectReservationCommand toCommand(Long reservationId, Long trainerId) {
    return new RejectReservationCommand(reservationId, trainerId, rejectionReason);
  }
}
