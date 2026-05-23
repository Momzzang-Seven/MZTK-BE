package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.GetReservationEscrowOrderUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;

@RequiredArgsConstructor
public class GetReservationEscrowOrderService implements GetReservationEscrowOrderUseCase {

  private final LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;

  @Override
  public ReservationEscrowOrderView getOrder(String orderKey) {
    return loadReservationEscrowOrderPort.getOrder(orderKey);
  }

  @Override
  public List<ReservationEscrowOrderView> getOrders(Collection<String> orderKeys) {
    return loadReservationEscrowOrderPort.getOrders(orderKeys);
  }
}
