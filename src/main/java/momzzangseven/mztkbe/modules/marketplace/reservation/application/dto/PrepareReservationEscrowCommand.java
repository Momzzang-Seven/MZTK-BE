package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/** Reservation-owned snapshot used to prepare a marketplace Web3 user execution. */
public record PrepareReservationEscrowCommand(
    Long reservationId,
    String orderId,
    Long requesterUserId,
    Long buyerUserId,
    Long trainerUserId,
    Long reservationVersion,
    String buyerWalletAddress,
    String trainerWalletAddress,
    Integer bookedPriceAmountKrw,
    LocalDateTime sessionEndAt) {

  public PrepareReservationEscrowCommand {
    requirePositive(reservationId, "reservationId");
    requireUuid(orderId, "orderId");
    requirePositive(requesterUserId, "requesterUserId");
    requirePositive(buyerUserId, "buyerUserId");
    requirePositive(trainerUserId, "trainerUserId");
    if (reservationVersion == null || reservationVersion < 0) {
      throw new IllegalArgumentException("reservationVersion must be >= 0");
    }
    requireText(buyerWalletAddress, "buyerWalletAddress");
    requireText(trainerWalletAddress, "trainerWalletAddress");
    if (bookedPriceAmountKrw == null || bookedPriceAmountKrw <= 0) {
      throw new IllegalArgumentException("bookedPriceAmountKrw must be positive");
    }
    if (sessionEndAt == null) {
      throw new IllegalArgumentException("sessionEndAt is required");
    }
  }

  private static void requirePositive(Long value, String fieldName) {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException(fieldName + " must be positive");
    }
  }

  private static void requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
  }

  private static void requireUuid(String value, String fieldName) {
    requireText(value, fieldName);
    try {
      UUID.fromString(value);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(fieldName + " must be a UUID", ex);
    }
  }
}
