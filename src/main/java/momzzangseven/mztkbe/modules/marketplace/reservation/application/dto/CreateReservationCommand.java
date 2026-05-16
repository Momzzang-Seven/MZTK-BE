package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Command for creating a new reservation.
 *
 * <p>Call {@link #validate()} at the entry point of the use case to enforce structural invariants
 * before any persistence or on-chain interaction takes place.
 *
 * @param userId the authenticated user (buyer) ID
 * @param classId the class being reserved
 * @param slotId the target time-slot
 * @param reservationDate requested session date
 * @param reservationTime requested session start time
 * @param userRequest optional note from the user (nullable)
 * @param signedAmount the amount the user signed (used for price-mismatch validation)
 */
public record CreateReservationCommand(
    Long userId,
    Long classId,
    Long slotId,
    LocalDate reservationDate,
    LocalTime reservationTime,
    String userRequest,
    String idempotencyKey,
    BigInteger signedAmount) {

  public CreateReservationCommand(
      Long userId,
      Long classId,
      Long slotId,
      LocalDate reservationDate,
      LocalTime reservationTime,
      String userRequest,
      BigInteger signedAmount) {
    this(
        userId, classId, slotId, reservationDate, reservationTime, userRequest, null, signedAmount);
  }

  /**
   * Validates structural preconditions of the command.
   *
   * <p>Does not validate business rules (price match, capacity, etc.) — those belong in the
   * service. This method guards against obviously malformed input that would cause a
   * NullPointerException or data-corruption downstream.
   *
   * @throws IllegalArgumentException when any required field is null, non-positive, or blank
   */
  public void validate() {
    if (userId == null || userId <= 0) {
      throw new IllegalArgumentException("userId must be a positive number");
    }
    if (classId == null || classId <= 0) {
      throw new IllegalArgumentException("classId must be a positive number");
    }
    if (slotId == null || slotId <= 0) {
      throw new IllegalArgumentException("slotId must be a positive number");
    }
    if (reservationDate == null) {
      throw new IllegalArgumentException("reservationDate must not be null");
    }
    if (reservationTime == null) {
      throw new IllegalArgumentException("reservationTime must not be null");
    }
    if (signedAmount == null || signedAmount.signum() <= 0) {
      throw new IllegalArgumentException("signedAmount must be a positive value");
    }
  }
}
