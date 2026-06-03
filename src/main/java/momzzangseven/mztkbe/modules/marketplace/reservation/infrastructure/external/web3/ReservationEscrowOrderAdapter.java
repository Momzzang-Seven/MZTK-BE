package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowOrderResult;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.GetMarketplaceEscrowOrderUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Cross-module adapter for reading marketplace escrow order state from the Web3 module. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
@Primary
public class ReservationEscrowOrderAdapter implements LoadReservationEscrowOrderPort {

  private final GetMarketplaceEscrowOrderUseCase getMarketplaceEscrowOrderUseCase;

  @Override
  public ReservationEscrowOrderView getOrder(String orderKey) {
    return toView(getMarketplaceEscrowOrderUseCase.getOrder(orderKey));
  }

  @Override
  public List<ReservationEscrowOrderView> getOrders(Collection<String> orderKeys) {
    if (orderKeys == null || orderKeys.isEmpty()) {
      return List.of();
    }
    return getMarketplaceEscrowOrderUseCase.getOrders(orderKeys.stream().toList()).stream()
        .map(this::toView)
        .toList();
  }

  private ReservationEscrowOrderView toView(MarketplaceEscrowOrderResult order) {
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
