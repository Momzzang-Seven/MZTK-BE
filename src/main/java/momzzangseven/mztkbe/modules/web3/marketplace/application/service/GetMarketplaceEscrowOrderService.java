package momzzangseven.mztkbe.modules.web3.marketplace.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowOrderResult;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.GetMarketplaceEscrowOrderUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceEscrowOrderPort;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.MarketplaceEscrowOrderView;

@RequiredArgsConstructor
public class GetMarketplaceEscrowOrderService implements GetMarketplaceEscrowOrderUseCase {

  private final LoadMarketplaceEscrowOrderPort loadMarketplaceEscrowOrderPort;

  @Override
  public MarketplaceEscrowOrderResult getOrder(String orderKey) {
    return toResult(loadMarketplaceEscrowOrderPort.getOrder(orderKey));
  }

  @Override
  public List<MarketplaceEscrowOrderResult> getOrders(List<String> orderKeys) {
    if (orderKeys == null || orderKeys.isEmpty()) {
      return List.of();
    }
    return loadMarketplaceEscrowOrderPort.getOrders(orderKeys).stream()
        .map(this::toResult)
        .toList();
  }

  private MarketplaceEscrowOrderResult toResult(MarketplaceEscrowOrderView view) {
    return new MarketplaceEscrowOrderResult(
        view.orderKey(),
        view.price(),
        view.tokenAddress(),
        view.deadlineEpochSeconds(),
        view.state(),
        view.buyerAddress(),
        view.trainerAddress());
  }
}
