package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

public record ReservationSummaryResponseDTO(
    Long reservationId,
    Long slotId,
    Long trainerId,
    Long userId,
    LocalDate reservationDate,
    LocalTime reservationTime,
    int durationMinutes,
    ReservationStatus status,
    String userRequest) {

  public static ReservationSummaryResponseDTO from(ReservationSummaryResult result) {
    return new ReservationSummaryResponseDTO(
        result.reservationId(),
        result.slotId(),
        result.trainerId(),
        result.userId(),
        result.reservationDate(),
        result.reservationTime(),
        result.durationMinutes(),
        result.status(),
        result.userRequest());
  }
}
