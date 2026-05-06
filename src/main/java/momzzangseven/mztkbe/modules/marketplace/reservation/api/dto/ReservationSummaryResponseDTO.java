package momzzangseven.mztkbe.modules.marketplace.reservation.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationSummaryResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * HTTP response DTO for the reservation list endpoints.
 *
 * <p>Used by:
 *
 * <ul>
 *   <li>{@code GET /marketplace/me/reservations}
 *   <li>{@code GET /marketplace/trainer/reservations}
 * </ul>
 */
public record ReservationSummaryResponseDTO(
    Long reservationId,
    Long slotId,
    Long trainerId,
    Long userId,
    LocalDate reservationDate,
    LocalTime reservationTime,
    int durationMinutes,
    ReservationStatus status,
    String userRequest,
    String classTitle,
    String trainerNickname,
    String thumbnailFinalObjectKey) {

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
        result.userRequest(),
        result.classTitle(),
        result.trainerNickname(),
        result.thumbnailFinalObjectKey());
  }
}
