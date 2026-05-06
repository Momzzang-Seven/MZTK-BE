package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort.ClassSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort.UserSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * Summary item for a reservation list response.
 *
 * <p>Intentionally omits sensitive fields (e.g., {@code orderId}, {@code txHash}) that are only
 * relevant on the detail view.
 *
 * <p>Enrichment fields ({@code classTitle}, {@code trainerNickname}, {@code
 * thumbnailFinalObjectKey}) are populated from cross-module lookups performed by the service layer.
 * They may be {@code null} if the referenced data is unavailable.
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
 * @param classTitle class title; {@code null} if class data is unavailable
 * @param trainerNickname trainer's display nickname; {@code null} if user data is unavailable
 * @param thumbnailFinalObjectKey S3 object key for the class thumbnail; {@code null} if not set
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
    String userRequest,
    String classTitle,
    String trainerNickname,
    String thumbnailFinalObjectKey) {

  /**
   * Build a summary from a reservation domain object enriched with cross-module summaries.
   *
   * @param reservation reservation domain model
   * @param classSummary class summary from the classes module; may be {@code null}
   * @param trainerSummary trainer user summary from the user module; may be {@code null}
   * @return populated result record
   */
  public static ReservationSummaryResult from(
      Reservation reservation, ClassSummary classSummary, UserSummary trainerSummary) {
    return new ReservationSummaryResult(
        reservation.getId(),
        reservation.getSlotId(),
        reservation.getTrainerId(),
        reservation.getUserId(),
        reservation.getReservationDate(),
        reservation.getReservationTime(),
        reservation.getDurationMinutes(),
        reservation.getStatus(),
        reservation.getUserRequest(),
        classSummary != null ? classSummary.title() : null,
        trainerSummary != null ? trainerSummary.nickname() : null,
        classSummary != null ? classSummary.thumbnailFinalObjectKey() : null);
  }
}
