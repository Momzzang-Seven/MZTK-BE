package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/** Reservation-owned snapshot used to prepare a marketplace Web3 user execution. */
public record PrepareReservationEscrowCommand(
    Long reservationId,
    String orderId,
    String orderKey,
    String actorType,
    Long authorityUserId,
    Long requesterUserId,
    Long counterpartyUserId,
    Long buyerUserId,
    Long trainerUserId,
    Long reservationVersion,
    ReservationStatus expectedReservationStatus,
    ReservationEscrowStatus expectedEscrowStatus,
    String buyerWalletAddress,
    String trainerWalletAddress,
    String tokenAddress,
    String priceBaseUnits,
    Integer bookedPriceAmountKrw,
    LocalDateTime sessionEndAt,
    Long expectedContractDeadlineEpochSeconds,
    Long contractDeadlineEpochSeconds,
    String pendingAttemptToken,
    String targetTerminalStatus) {

  public PrepareReservationEscrowCommand {
    requirePositive(reservationId, "reservationId");
    requireUuid(orderId, "orderId");
    if (orderKey == null || orderKey.isBlank()) {
      orderKey = orderKeyFromUuid(orderId);
    }
    if (actorType == null || actorType.isBlank()) {
      actorType = "BUYER";
    }
    if (authorityUserId == null) {
      authorityUserId = requesterUserId;
    }
    requirePositive(authorityUserId, "authorityUserId");
    requirePositive(requesterUserId, "requesterUserId");
    if (counterpartyUserId == null) {
      counterpartyUserId =
          requesterUserId != null && requesterUserId.equals(buyerUserId)
              ? trainerUserId
              : buyerUserId;
    }
    requirePositive(buyerUserId, "buyerUserId");
    requirePositive(trainerUserId, "trainerUserId");
    if (reservationVersion == null || reservationVersion < 0) {
      throw new IllegalArgumentException("reservationVersion must be >= 0");
    }
    requireText(buyerWalletAddress, "buyerWalletAddress");
    requireText(trainerWalletAddress, "trainerWalletAddress");
    if (tokenAddress == null || tokenAddress.isBlank()) {
      tokenAddress = "0x0000000000000000000000000000000000000000";
    }
    if (priceBaseUnits == null || priceBaseUnits.isBlank()) {
      priceBaseUnits = String.valueOf(bookedPriceAmountKrw);
    }
    if (bookedPriceAmountKrw == null || bookedPriceAmountKrw <= 0) {
      throw new IllegalArgumentException("bookedPriceAmountKrw must be positive");
    }
    if (sessionEndAt == null) {
      throw new IllegalArgumentException("sessionEndAt is required");
    }
  }

  public PrepareReservationEscrowCommand(
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
    this(
        reservationId,
        orderId,
        null,
        "BUYER",
        requesterUserId,
        requesterUserId,
        null,
        buyerUserId,
        trainerUserId,
        reservationVersion,
        null,
        null,
        buyerWalletAddress,
        trainerWalletAddress,
        null,
        null,
        bookedPriceAmountKrw,
        sessionEndAt,
        null,
        null,
        null,
        null);
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

  private static String orderKeyFromUuid(String orderId) {
    UUID uuid = UUID.fromString(orderId);
    return "0x"
        + "0".repeat(32)
        + String.format(
            java.util.Locale.ROOT,
            "%016x%016x",
            uuid.getMostSignificantBits(),
            uuid.getLeastSignificantBits());
  }
}
