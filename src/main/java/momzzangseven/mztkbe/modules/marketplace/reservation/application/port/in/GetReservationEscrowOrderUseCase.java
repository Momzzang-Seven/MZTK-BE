package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in;

import java.util.Collection;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;

/** Input port for reservation-owned marketplace escrow order reads. */
public interface GetReservationEscrowOrderUseCase {

  ReservationEscrowOrderView getOrder(String orderKey);

  List<ReservationEscrowOrderView> getOrders(Collection<String> orderKeys);
}
