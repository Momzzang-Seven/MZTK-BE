package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadClassSummaryPort.ClassSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort.UserSummary;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * Result containing the full detail of a single reservation.
 *
 * <p>Enrichment fields ({@code classTitle}, {@code priceAmount}, {@code trainerNickname}, {@code
 * userNickname}, {@code thumbnailFinalObjectKey}) are populated from cross-module lookups. They may
 * be {@code null} (or {@code 0} for {@code priceAmount}) if the referenced data is unavailable.
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
 * @param classTitle class title; {@code null} if class data is unavailable
 * @param priceAmount class price in KRW; {@code 0} if class data is unavailable
 * @param trainerNickname trainer's display nickname; {@code null} if user data is unavailable
 * @param userNickname reserving user's display nickname; {@code null} if user data is unavailable
 * @param thumbnailFinalObjectKey S3 object key for the class thumbnail; {@code null} if not set
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
    LocalDateTime updatedAt,
    String classTitle,
    int priceAmount,
    String trainerNickname,
    String userNickname,
    String thumbnailFinalObjectKey) {

  /**
   * Build a detail result from a reservation domain object enriched with cross-module summaries.
   *
   * @param reservation reservation domain model
   * @param classSummary class summary from the classes module; may be {@code null}
   * @param trainerSummary trainer user summary from the user module; may be {@code null}
   * @param userSummary reserving user summary from the user module; may be {@code null}
   * @return populated result record
   */
  public static GetReservationResult from(
      Reservation reservation,
      ClassSummary classSummary,
      UserSummary trainerSummary,
      UserSummary userSummary) {
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
        reservation.getUpdatedAt(),
        classSummary != null ? classSummary.title() : null,
        classSummary != null ? classSummary.priceAmount() : 0,
        trainerSummary != null ? trainerSummary.nickname() : null,
        userSummary != null ? userSummary.nickname() : null,
        classSummary != null ? classSummary.thumbnailFinalObjectKey() : null);
  }
}
