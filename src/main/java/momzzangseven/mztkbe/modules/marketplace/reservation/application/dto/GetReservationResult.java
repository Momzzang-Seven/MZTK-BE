package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * Result containing the full detail of a single reservation.
 *
 * @param reservationId primary key
 * @param userId reserving user's ID
 * @param trainerId trainer's ID
 * @param slotId class slot ID
 * @param reservationDate scheduled session date
 * @param reservationTime session start time
 * @param durationMinutes session duration
 * @param status current lifecycle status
 * @param userRequest optional note from the user
 * @param orderId server-generated escrow order ID
 * @param txHash most recent on-chain transaction hash
 * @param createdAt reservation creation timestamp
 * @param updatedAt last update timestamp
 */
public record GetReservationResult(
    Long reservationId,
    Long userId,
    Long trainerId,
    Long slotId,
    LocalDate reservationDate,
    LocalTime reservationTime,
    int durationMinutes,
    ReservationStatus status,
    String userRequest,
    String orderId,
    String txHash,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static GetReservationResult from(Reservation reservation) {
    return new GetReservationResult(
        reservation.getId(),
        reservation.getUserId(),
        reservation.getTrainerId(),
        reservation.getSlotId(),
        reservation.getReservationDate(),
        reservation.getReservationTime(),
        reservation.getDurationMinutes(),
        reservation.getStatus(),
        reservation.getUserRequest(),
        reservation.getOrderId(),
        reservation.getTxHash(),
        reservation.getCreatedAt(),
        reservation.getUpdatedAt());
  }
}
