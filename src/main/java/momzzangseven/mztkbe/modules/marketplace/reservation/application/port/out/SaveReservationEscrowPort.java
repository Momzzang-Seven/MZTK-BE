package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationEscrow;

public interface SaveReservationEscrowPort {

  MarketplaceReservationEscrow save(MarketplaceReservationEscrow escrow);
}
