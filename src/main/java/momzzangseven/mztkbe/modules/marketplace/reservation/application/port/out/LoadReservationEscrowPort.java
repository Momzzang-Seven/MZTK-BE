package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationEscrow;

public interface LoadReservationEscrowPort {

  Optional<MarketplaceReservationEscrow> findByReservationId(Long reservationId);

  Optional<MarketplaceReservationEscrow> findByReservationIdWithLock(Long reservationId);

  Optional<MarketplaceReservationEscrow> findByOrderKey(String orderKey);
}
