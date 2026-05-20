package momzzangseven.mztkbe.modules.web3.marketplace.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowOrderResult;

public interface GetMarketplaceEscrowOrderUseCase {

  MarketplaceEscrowOrderResult getOrder(String orderKey);

  List<MarketplaceEscrowOrderResult> getOrders(List<String> orderKeys);
}
