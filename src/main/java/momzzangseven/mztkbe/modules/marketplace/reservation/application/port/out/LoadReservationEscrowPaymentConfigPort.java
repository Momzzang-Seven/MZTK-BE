package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.math.BigInteger;

/**
 * Reservation-owned output port for marketplace escrow payment configuration.
 *
 * <p>MOM-313 fixes the on-chain policy as: the class {@code priceAmount} remains the existing KRW
 * display amount, and the escrow charge is the same whole-unit amount in the reward token converted
 * to ERC-20 base units. If product changes away from "30,000 KRW display price charges 30,000
 * MZTK", replace this record method with an explicit KRW-to-token pricing port before signing
 * marketplace calldata.
 */
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
