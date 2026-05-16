package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;

/** Reservation-owned purchase precheck snapshot sent through an output port. */
public record PrecheckReservationPurchaseCommand(
    Long buyerUserId,
    Long trainerUserId,
    Long classId,
    Long slotId,
    LocalDate reservationDate,
    LocalTime reservationTime,
    BigInteger signedAmount,
    Integer bookedPriceAmountKrw) {

  public PrecheckReservationPurchaseCommand {
    requirePositive(buyerUserId, "buyerUserId");
    requirePositive(trainerUserId, "trainerUserId");
    requirePositive(classId, "classId");
    requirePositive(slotId, "slotId");
    if (reservationDate == null) {
      throw new IllegalArgumentException("reservationDate is required");
    }
    if (reservationTime == null) {
      throw new IllegalArgumentException("reservationTime is required");
    }
    if (signedAmount == null || signedAmount.signum() <= 0) {
      throw new IllegalArgumentException("signedAmount must be positive");
    }
    if (bookedPriceAmountKrw == null || bookedPriceAmountKrw <= 0) {
      throw new IllegalArgumentException("bookedPriceAmountKrw must be positive");
    }
  }

  private static void requirePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException(fieldName + " must be positive");
    }
  }
}
