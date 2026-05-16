package momzzangseven.mztkbe.global.error.marketplace;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/** Thrown when marketplace user on-chain flow is called while Web3 execution is disabled. */
public class MarketplaceWeb3DisabledException extends BusinessException {

  public MarketplaceWeb3DisabledException() {
    super(ErrorCode.MARKETPLACE_WEB3_DISABLED);
  }
}
