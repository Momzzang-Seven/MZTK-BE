package momzzangseven.mztkbe.global.error.treasury;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/**
 * Raised when a treasury wallet provisioning request collides with an existing row on alias or
 * address.
 */
public class TreasuryWalletAlreadyProvisionedException extends BusinessException {

  public TreasuryWalletAlreadyProvisionedException(String message) {
    super(ErrorCode.TREASURY_WALLET_ALREADY_PROVISIONED, message);
  }
}
