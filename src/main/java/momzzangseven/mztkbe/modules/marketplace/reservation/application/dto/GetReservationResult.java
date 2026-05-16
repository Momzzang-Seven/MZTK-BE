package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * Result containing the full detail of a single reservation.
 *
 * <h2>Enrichment strategy</h2>
 *
 * <ul>
 *   <li>{@code classTitle} and {@code priceAmount} — read from the denormalised snapshot fields
 *       ({@code bookedClassTitle} / {@code bookedPriceAmount}) written at booking time. They
 *       reflect the values the user agreed to when booking and are <b>immutable</b>: a trainer
 *       changing the class price or title after the fact does <em>not</em> affect past
 *       reservations. Legacy records (created before the snapshot columns were added, identifiable
 *       by {@code bookedPriceAmount == null}) fall back to a live cross-module lookup via {@code
 *       LoadClassSummaryPort}.
 *   <li>{@code thumbnailFinalObjectKey} — resolved live (no snapshot exists). May be {@code null}
 *       if the class is inactive or the thumbnail has been removed.
 *   <li>{@code trainerNickname} / {@code userNickname} — resolved live from the user module.
 * </ul>
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
 * @param classTitle class title <b>at booking time</b> (snapshot); {@code null} only for legacy
 *     records
 * @param priceAmount price in KRW <b>at booking time</b> (snapshot) — the actual escrow amount the
 *     user paid; {@code null} only for legacy records
 * @param trainerNickname trainer's current display nickname; {@code null} if unavailable
 * @param userNickname reserving user's current display nickname; {@code null} if unavailable
 * @param thumbnailFinalObjectKey S3 object key for the class thumbnail (live); {@code null} if not
 *     set
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
    Integer priceAmount,
    String trainerNickname,
    String userNickname,
    String thumbnailFinalObjectKey,
    ReservationExecutionResumeView web3Execution) {

  /**
   * Build a detail result from a reservation domain object.
   *
   * <p>{@code classTitle} and {@code priceAmount} must be sourced from the booking-time snapshot
   * ({@code reservation.getBookedClassTitle()} / {@code reservation.getBookedPriceAmount()}) rather
   * than from a live cross-module lookup. {@link
   * momzzangseven.mztkbe.modules.marketplace.reservation.application.service.GetReservationDetailService}
   * is responsible for applying the snapshot-first strategy before invoking this factory.
   *
   * @param reservation reservation domain model
   * @param classTitle class title at booking time (from snapshot); {@code null} for legacy records
   * @param priceAmount price in KRW at booking time (from snapshot); {@code null} for legacy
   *     records
   * @param thumbnailFinalObjectKey S3 thumbnail key (live lookup); {@code null} if not set
   * @param trainerNickname trainer's current display nickname; {@code null} if unavailable
   * @param userNickname reserving user's current display nickname; {@code null} if unavailable
   * @return populated result record
   */
  public static GetReservationResult from(
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

  public static GetReservationResult from(
      Reservation reservation,
      String classTitle,
      Integer priceAmount,
      String thumbnailFinalObjectKey,
      String trainerNickname,
      String userNickname,
      ReservationExecutionResumeView web3Execution) {
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
        classTitle,
        priceAmount,
        trainerNickname,
        userNickname,
        thumbnailFinalObjectKey,
        web3Execution);
  }
}
