package momzzangseven.mztkbe.modules.web3.marketplace.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionCleanupIntent;

public interface LoadMarketplaceExecutionCleanupIntentPort {

  List<MarketplaceExecutionCleanupIntent> loadByIds(List<Long> intentIds);
}
