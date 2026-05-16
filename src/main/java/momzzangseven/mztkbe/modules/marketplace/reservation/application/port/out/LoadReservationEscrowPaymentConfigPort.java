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

    public BigInteger priceBaseUnits(BigInteger signedAmount) {
      if (signedAmount == null || signedAmount.signum() <= 0) {
        throw new IllegalArgumentException("signedAmount must be positive");
      }
      return signedAmount;
    }
  }
}
