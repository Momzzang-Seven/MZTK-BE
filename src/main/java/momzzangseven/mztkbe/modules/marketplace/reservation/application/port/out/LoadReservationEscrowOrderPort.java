package momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out;

import java.util.Collection;
import java.util.List;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;

/** Output port for reservation-scoped marketplace escrow order reads. */
public interface LoadReservationEscrowOrderPort {

  ReservationEscrowOrderView getOrder(String orderKey);

  List<ReservationEscrowOrderView> getOrders(Collection<String> orderKeys);
}
