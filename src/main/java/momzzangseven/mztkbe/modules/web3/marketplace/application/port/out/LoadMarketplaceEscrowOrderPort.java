package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

import java.util.List;

public interface LoadMarketplaceEscrowOrderPort {

  MarketplaceEscrowOrderView getOrder(String orderKey);

  List<MarketplaceEscrowOrderView> getOrders(List<String> orderKeys);
}
