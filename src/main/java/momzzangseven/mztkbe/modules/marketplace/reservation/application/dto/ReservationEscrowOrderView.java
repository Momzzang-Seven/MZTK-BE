package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

/** Reservation-owned view of the marketplace escrow contract order state. */
public record ReservationEscrowOrderView(
    String orderKey,
    String priceBaseUnits,
    String tokenAddress,
    Long deadlineEpochSeconds,
    int state,
    String buyerAddress,
    String trainerAddress) {

  public static final int STATE_ABSENT = 0;
  public static final int STATE_CREATED = 1000;
  public static final int STATE_CONFIRMED = 2000;
  public static final int STATE_CANCELLED = 3000;
  public static final int STATE_ADMIN_SETTLED = 4000;
  public static final int STATE_ADMIN_REFUNDED = 5000;
  public static final int STATE_DEADLINE_REFUNDED = 6000;

  public boolean isAbsent() {
    return state == STATE_ABSENT || isZeroAddress(buyerAddress);
  }

  private static boolean isZeroAddress(String address) {
    return address == null
        || "0x0000000000000000000000000000000000000000".equalsIgnoreCase(address);
  }
}
