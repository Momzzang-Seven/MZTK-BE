package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.util.Optional;

/** Reservation-owned output port for resolving active Web3 wallets. */
public interface LoadReservationWalletPort {

  Optional<String> loadActiveWalletAddress(Long userId);
}
