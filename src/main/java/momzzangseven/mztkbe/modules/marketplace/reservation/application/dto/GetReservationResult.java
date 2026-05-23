package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationTerminalResolvedBy;

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
    ReservationDisplayStatus status,
    ReservationStatus businessStatus,
    ReservationEscrowStatus escrowStatus,
    String userRequest,
    String orderId,
    String orderKey,
    String txHash,
    LocalDateTime contractDeadlineAt,
    Long contractDeadlineEpochSeconds,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String classTitle,
    Integer priceAmount,
    String trainerNickname,
    String userNickname,
    String thumbnailFinalObjectKey,
    ReservationTerminalResolvedBy resolvedBy,
    String terminalReasonCode,
    ReservationViewerActions viewerActions,
    ReservationExecutionResumeView web3Execution) {}
