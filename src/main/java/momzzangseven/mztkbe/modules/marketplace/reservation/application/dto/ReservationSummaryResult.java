package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * Summary item for a reservation list response.
 *
 * <p>Intentionally omits sensitive fields (e.g., {@code orderId}, {@code txHash}) that are only
 * relevant on the detail view.
 *
 * @param reservationId primary key
 * @param slotId class slot ID
 * @param trainerId trainer's ID
 * @param userId reserving user's ID
 * @param reservationDate scheduled session date
 * @param reservationTime session start time
 * @param durationMinutes session duration in minutes
 * @param status current lifecycle status
 * @param userRequest optional note from the user
 */
public record ReservationSummaryResult(
    Long reservationId,
    Long slotId,
    Long trainerId,
    Long userId,
    LocalDate reservationDate,
    LocalTime reservationTime,
    int durationMinutes,
    ReservationStatus status,
    String userRequest) {

  public static ReservationSummaryResult from(Reservation reservation) {
    return new ReservationSummaryResult(
        reservation.getId(),
        reservation.getSlotId(),
        reservation.getTrainerId(),
        reservation.getUserId(),
        reservation.getReservationDate(),
        reservation.getReservationTime(),
        reservation.getDurationMinutes(),
        reservation.getStatus(),
        reservation.getUserRequest());
  }
}
