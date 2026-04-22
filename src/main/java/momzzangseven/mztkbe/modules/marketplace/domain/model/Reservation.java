package momzzangseven.mztkbe.modules.marketplace.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.ReservationStatus;

/**
 * Aggregate root representing a single class reservation.
 *
 * <p>All fields are {@code private final} to enforce immutability. State transitions are expressed
 * as methods that return a new instance via {@code toBuilder()} — never mutate in place.
 *
 * <h2>Denormalised fields</h2>
 *
 * <ul>
 *   <li>{@code trainerId} — copied from MarketplaceClass at creation for fast ownership checks.
 *   <li>{@code durationMinutes} — copied from ClassSlot so the auto-settle scheduler can compute
 *       session end-time without a cross-module query.
 * </ul>
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Reservation {

  private final Long id;

  /** User who made the reservation. */
  private final Long userId;

  /**
   * Trainer who owns the class. Denormalised from MarketplaceClass at creation time to avoid a
   * cross-module lookup during approve/reject operations.
   */
  private final Long trainerId;

  /** The class slot this reservation targets. */
  private final Long slotId;

  /** Actual date of the session. */
  private final LocalDate reservationDate;

  /** Start time of the session. */
  private final LocalTime reservationTime;

  /**
   * Session duration in minutes. Denormalised from ClassSlot so auto-settlement can compute class
   * end-time ({@code reservationDate + reservationTime + durationMinutes}) without an extra join.
   */
  private final int durationMinutes;

  /** Current lifecycle status of this reservation. */
  private final ReservationStatus status;

  /** Optional note from the user (max 500 chars). */
  private final String userRequest;

  /** Server-generated UUID used as the on-chain order identifier in the escrow contract. */
  private final String orderId;

  /** Most recent on-chain transaction hash associated with this reservation. */
  private final String txHash;

  /** JPA optimistic-lock version. Null for unsaved instances. */
  private final Long version;

  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  // ============================================================
  // Factory
  // ============================================================

  /**
   * Create a new reservation in the {@link ReservationStatus#PENDING} state.
   *
   * @param userId the reserving user's ID
   * @param trainerId trainer's ID (denormalised from the class)
   * @param slotId target time-slot ID
   * @param reservationDate scheduled session date
   * @param reservationTime session start time
   * @param durationMinutes session duration (denormalised from slot)
   * @param userRequest optional user note (nullable)
   * @param orderId server-generated UUID for the escrow contract
   * @param txHash on-chain transaction hash returned by the escrow submit
   * @return new PENDING reservation
   */
  public static Reservation createPending(
      Long userId,
      Long trainerId,
      Long slotId,
      LocalDate reservationDate,
      LocalTime reservationTime,
      int durationMinutes,
      String userRequest,
      String orderId,
      String txHash) {

    return Reservation.builder()
        .userId(userId)
        .trainerId(trainerId)
        .slotId(slotId)
        .reservationDate(reservationDate)
        .reservationTime(reservationTime)
        .durationMinutes(durationMinutes)
        .status(ReservationStatus.PENDING)
        .userRequest(userRequest)
        .orderId(orderId)
        .txHash(txHash)
        .build();
  }

  // ============================================================
  // State-transition methods
  // ============================================================

  /**
   * Approve this reservation (PENDING → APPROVED).
   *
   * @return new APPROVED reservation
   * @throws BusinessException when current status does not allow APPROVED transition
   */
  public Reservation approve() {
    guardTransition(ReservationStatus.APPROVED);
    return toBuilder().status(ReservationStatus.APPROVED).build();
  }

  /**
   * Cancel this reservation by the user (PENDING → USER_CANCELLED).
   *
   * @param cancelTxHash on-chain transaction hash of the cancelClass call
   * @return new USER_CANCELLED reservation
   */
  public Reservation cancelByUser(String cancelTxHash) {
    guardTransition(ReservationStatus.USER_CANCELLED);
    return toBuilder().status(ReservationStatus.USER_CANCELLED).txHash(cancelTxHash).build();
  }

  /**
   * Reject this reservation by the trainer (PENDING → REJECTED).
   *
   * @param rejectionTxHash on-chain transaction hash of the cancelClass call
   * @return new REJECTED reservation
   */
  public Reservation reject(String rejectionTxHash) {
    guardTransition(ReservationStatus.REJECTED);
    return toBuilder().status(ReservationStatus.REJECTED).txHash(rejectionTxHash).build();
  }

  /**
   * Mark as settled by the user after class completion (APPROVED → SETTLED).
   *
   * @param confirmTxHash on-chain transaction hash of the confirmClass call
   * @return new SETTLED reservation
   */
  public Reservation complete(String confirmTxHash) {
    guardTransition(ReservationStatus.SETTLED);
    return toBuilder().status(ReservationStatus.SETTLED).txHash(confirmTxHash).build();
  }

  /**
   * Mark as auto-cancelled due to trainer inactivity (PENDING → TIMEOUT_CANCELLED).
   *
   * @param refundTxHash on-chain transaction hash of the adminRefund call
   * @return new TIMEOUT_CANCELLED reservation
   */
  public Reservation timeoutCancel(String refundTxHash) {
    guardTransition(ReservationStatus.TIMEOUT_CANCELLED);
    return toBuilder().status(ReservationStatus.TIMEOUT_CANCELLED).txHash(refundTxHash).build();
  }

  /**
   * Mark as auto-settled by the scheduler (APPROVED → AUTO_SETTLED).
   *
   * @param settleTxHash on-chain transaction hash of the adminSettle call
   * @return new AUTO_SETTLED reservation
   */
  public Reservation autoSettle(String settleTxHash) {
    guardTransition(ReservationStatus.AUTO_SETTLED);
    return toBuilder().status(ReservationStatus.AUTO_SETTLED).txHash(settleTxHash).build();
  }

  // ============================================================
  // Domain query helpers
  // ============================================================

  /** Returns true when this reservation is owned by the given user. */
  public boolean isOwnedByUser(Long targetUserId) {
    return userId != null && userId.equals(targetUserId);
  }

  /** Returns true when this reservation belongs to the given trainer. */
  public boolean isOwnedByTrainer(Long targetTrainerId) {
    return trainerId != null && trainerId.equals(targetTrainerId);
  }

  /** Computes the session end {@link LocalDateTime} using the denormalised duration. */
  public LocalDateTime sessionEndAt() {
    return LocalDateTime.of(reservationDate, reservationTime).plusMinutes(durationMinutes);
  }

  // ============================================================
  // Private helpers
  // ============================================================

  private void guardTransition(ReservationStatus next) {
    if (!status.canTransitionTo(next)) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Cannot transition from " + status + " to " + next);
    }
  }
}
