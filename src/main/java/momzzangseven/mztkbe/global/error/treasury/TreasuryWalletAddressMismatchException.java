package momzzangseven.mztkbe.global.error.treasury;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

/**
 * Raised when the address derived from {@code rawPrivateKey} does not match the operator-supplied
 * {@code expectedAddress}. Defence-in-depth check against fat-fingered key paste during treasury
 * provisioning.
 */
public class TreasuryWalletAddressMismatchException extends BusinessException {

  public TreasuryWalletAddressMismatchException(String message) {
    super(ErrorCode.TREASURY_WALLET_ADDRESS_MISMATCH, message);
  }
}
