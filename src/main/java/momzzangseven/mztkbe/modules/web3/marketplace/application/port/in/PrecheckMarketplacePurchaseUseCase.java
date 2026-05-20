package momzzangseven.mztkbe.modules.web3.marketplace.application.port.in;

import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.PrecheckMarketplacePurchaseCommand;

/** Read-only precheck before marketplace reservation purchase state is retained locally. */
public interface PrecheckMarketplacePurchaseUseCase {

  void precheck(PrecheckMarketplacePurchaseCommand command);
}
