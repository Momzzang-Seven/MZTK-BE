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
   * service. This method guards against obviously malformed input that would cause a
   * NullPointerException or data-corruption downstream, including basic EIP-7702 signature format
   * checks.
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
    validateSignature("delegationSignature", delegationSignature);
    validateSignature("executionSignature", executionSignature);
  }

  /**
   * Validates that a Web3 signature string is structurally sound:
   *
   * <ul>
   *   <li>Not null or blank
   *   <li>Starts with {@code "0x"} (EIP prefix)
   *   <li>At least 132 characters total ({@code "0x"} + 130 hex chars for a 65-byte ECDSA sig)
   * </ul>
   *
   * <p>Does NOT perform cryptographic verification — that is deferred to the relayer (web3 module).
   */
  private static void validateSignature(String fieldName, String sig) {
    if (sig == null || sig.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    if (!sig.startsWith("0x")) {
      throw new IllegalArgumentException(fieldName + " must start with '0x'");
    }
    // EIP-7702 / ECDSA: 0x + 130 hex chars = 132 minimum
    if (sig.length() < 132) {
      throw new IllegalArgumentException(
          fieldName + " is too short (minimum 132 chars including '0x' prefix)");
    }
  }
}
