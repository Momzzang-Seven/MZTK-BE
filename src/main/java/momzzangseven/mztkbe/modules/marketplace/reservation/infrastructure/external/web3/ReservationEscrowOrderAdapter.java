package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceWeb3DisabledException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceEscrowOrderPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.MarketplaceEscrowOrderView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Cross-module adapter for reading marketplace escrow order state from the Web3 module. */
@Component
@RequiredArgsConstructor
public class ReservationEscrowOrderAdapter implements LoadReservationEscrowOrderPort {

  private final ObjectProvider<LoadMarketplaceEscrowOrderPort> loadMarketplaceEscrowOrderPort;

  @Override
  public ReservationEscrowOrderView getOrder(String orderKey) {
    return toView(delegate().getOrder(orderKey));
  }

  @Override
  public List<ReservationEscrowOrderView> getOrders(Collection<String> orderKeys) {
    if (orderKeys == null || orderKeys.isEmpty()) {
      return List.of();
    }
    return delegate().getOrders(orderKeys.stream().toList()).stream().map(this::toView).toList();
  }

  private LoadMarketplaceEscrowOrderPort delegate() {
    LoadMarketplaceEscrowOrderPort delegate = loadMarketplaceEscrowOrderPort.getIfAvailable();
    if (delegate == null) {
      throw new MarketplaceWeb3DisabledException();
    }
    return delegate;
  }

  private ReservationEscrowOrderView toView(MarketplaceEscrowOrderView order) {
    return new ReservationEscrowOrderView(
        order.orderKey(),
        order.price().toString(),
        order.tokenAddress(),
        order.deadlineEpochSeconds(),
        order.state(),
        order.buyerAddress(),
        order.trainerAddress());
  }
}
