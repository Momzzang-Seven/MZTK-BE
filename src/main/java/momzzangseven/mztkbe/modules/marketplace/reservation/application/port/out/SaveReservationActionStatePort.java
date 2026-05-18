package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;

public interface SaveReservationActionStatePort {

  MarketplaceReservationActionState save(MarketplaceReservationActionState actionState);
}
