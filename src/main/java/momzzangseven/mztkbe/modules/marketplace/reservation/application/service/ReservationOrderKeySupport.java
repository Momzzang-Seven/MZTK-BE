package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.util.Locale;
import java.util.UUID;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceReservationStateException;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;

/** Canonical marketplace escrow order-key handling for reservation application services. */
final class ReservationOrderKeySupport {

  private ReservationOrderKeySupport() {}

  static String fromOrderId(String orderId) {
    UUID uuid = UUID.fromString(orderId);
    return "0x"
        + "0".repeat(32)
        + String.format(
            Locale.ROOT,
            "%016x%016x",
            uuid.getMostSignificantBits(),
            uuid.getLeastSignificantBits());
  }

  static String requireOrderKey(Reservation reservation) {
    if (reservation.getOrderKey() != null && !reservation.getOrderKey().isBlank()) {
      return reservation.getOrderKey();
    }
    try {
      return fromOrderId(reservation.getOrderId());
    } catch (RuntimeException e) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_DEADLINE_SYNC_REQUIRED,
          "Reservation order key is missing and cannot be derived");
    }
  }
}
