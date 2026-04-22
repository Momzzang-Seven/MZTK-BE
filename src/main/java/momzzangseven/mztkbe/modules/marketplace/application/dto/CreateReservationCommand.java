package momzzangseven.mztkbe.modules.marketplace.application.dto;

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
 * @param delegationSignature EIP-7702 authorization tuple signature
 * @param executionSignature purchaseClass execution signature
 */
public record CreateReservationCommand(
    Long userId,
    Long classId,
    Long slotId,
    LocalDate reservationDate,
    LocalTime reservationTime,
    String userRequest,
    BigInteger signedAmount,
    String delegationSignature,
    String executionSignature) {

  /**
   * Validates structural preconditions of the command.
   *
   * <p>Does not validate business rules (price match, capacity, etc.) — those belong in the
   * service. This method only guards against obviously malformed input that would cause a
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
    if (delegationSignature == null || delegationSignature.isBlank()) {
      throw new IllegalArgumentException("delegationSignature must not be blank");
    }
    if (executionSignature == null || executionSignature.isBlank()) {
      throw new IllegalArgumentException("executionSignature must not be blank");
    }
  }
}
