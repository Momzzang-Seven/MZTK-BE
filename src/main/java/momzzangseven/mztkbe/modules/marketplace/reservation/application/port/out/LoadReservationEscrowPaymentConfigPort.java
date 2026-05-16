package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.math.BigInteger;

/** Reservation-owned output port for marketplace escrow payment configuration. */
public interface LoadReservationEscrowPaymentConfigPort {

  ReservationEscrowPaymentConfig load();

  record ReservationEscrowPaymentConfig(
      String tokenAddress, int decimals, long defaultDeadlineDurationSeconds) {

    public ReservationEscrowPaymentConfig(String tokenAddress, int decimals) {
      this(tokenAddress, decimals, 604_800L);
    }

    public BigInteger priceBaseUnits(Integer bookedPriceAmountKrw) {
      if (bookedPriceAmountKrw == null || bookedPriceAmountKrw <= 0) {
        throw new IllegalArgumentException("bookedPriceAmountKrw must be positive");
      }
      if (decimals < 0 || decimals > 36) {
        throw new IllegalArgumentException("decimals must be between 0 and 36");
      }
      return BigInteger.valueOf(bookedPriceAmountKrw).multiply(BigInteger.TEN.pow(decimals));
    }

    public BigInteger priceBaseUnits(BigInteger signedAmount, Integer bookedPriceAmountKrw) {
      if (signedAmount == null || signedAmount.signum() <= 0) {
        throw new IllegalArgumentException("signedAmount must be positive");
      }
      BigInteger expected = priceBaseUnits(bookedPriceAmountKrw);
      if (!signedAmount.equals(expected)) {
        throw new IllegalArgumentException("signedAmount must equal class price token base units");
      }
      return expected;
    }
  }
}
