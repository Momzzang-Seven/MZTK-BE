package momzzangseven.mztkbe.global.error.treasury;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/**
 * Thrown when a treasury wallet required to fulfil a request is missing (no row for the role's
 * alias) or present but not in {@code ACTIVE} status. Distinct from a generic 500 so operators /
 * the FE can recognise "service not yet provisioned" and route to admin provisioning instead of
 * treating it as an unexpected crash.
 */
public class TreasuryWalletNotProvisionedException extends BusinessException {

  public TreasuryWalletNotProvisionedException(String message) {
    super(ErrorCode.TREASURY_WALLET_NOT_PROVISIONED, message);
  }
}
