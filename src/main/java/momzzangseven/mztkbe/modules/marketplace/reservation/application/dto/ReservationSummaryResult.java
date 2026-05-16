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
 * <p>Enrichment fields ({@code classTitle}, {@code trainerNickname}, {@code userNickname}, {@code
 * thumbnailFinalObjectKey}) are populated from cross-module lookups performed by the service layer.
 * They may be {@code null} if the referenced data is unavailable.
 *
 * <p>{@code userNickname} is the reserving user's display name. It is populated on the trainer-list
 * path (so the trainer can identify who made each booking) and is {@code null} on the user-list
 * path (a user's own nickname is unnecessary when viewing their own history).
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
 * @param classTitle class title (snapshot); {@code null} if unavailable
 * @param priceAmount booking price in KRW (snapshot); {@code null} if unavailable (legacy record
 *     with no snapshot and no live adapter data)
 * @param trainerNickname trainer's display nickname; {@code null} if unavailable
 * @param userNickname reserving user's display nickname; populated on trainer-list path, {@code
 *     null} on user-list path
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
    Integer priceAmount,
    String trainerNickname,
    String userNickname,
    String thumbnailFinalObjectKey,
    ReservationExecutionResumeView web3Execution) {

  /**
   * Build a summary from a reservation domain object enriched with cross-module summaries.
   *
   * @param reservation reservation domain model
   * @param classTitle class title (snapshot); {@code null} if unavailable
   * @param priceAmount booking price; snapshot value if available, live adapter value otherwise
   * @param thumbnailFinalObjectKey S3 thumbnail key; {@code null} if not set
   * @param trainerNickname trainer's display nickname; {@code null} if unavailable
   * @param userNickname reserving user's display nickname; {@code null} on user-list path
   * @return populated result record
   */
  public static ReservationSummaryResult from(
      Reservation reservation,
      String classTitle,
      Integer priceAmount,
      String thumbnailFinalObjectKey,
      String trainerNickname,
      String userNickname) {
    return from(
        reservation,
        classTitle,
        priceAmount,
        thumbnailFinalObjectKey,
        trainerNickname,
        userNickname,
        null);
  }

  public static ReservationSummaryResult from(
      Reservation reservation,
      String classTitle,
      Integer priceAmount,
      String thumbnailFinalObjectKey,
      String trainerNickname,
      String userNickname,
      ReservationExecutionResumeView web3Execution) {
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
        classTitle,
        priceAmount,
        trainerNickname,
        userNickname,
        thumbnailFinalObjectKey,
        web3Execution);
  }
}
