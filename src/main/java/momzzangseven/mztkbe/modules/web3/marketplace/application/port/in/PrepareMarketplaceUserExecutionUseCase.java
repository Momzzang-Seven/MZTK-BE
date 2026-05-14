package momzzangseven.mztkbe.modules.web3.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceEscrowExecutionRequest;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionIntentResult;

/** Input port for preparing marketplace user execution intents. */
public interface PrepareMarketplaceUserExecutionUseCase {

  MarketplaceExecutionIntentResult prepare(MarketplaceEscrowExecutionRequest request);
}
