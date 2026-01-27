package momzzangseven.mztkbe.global.error.wallet;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class WalletNotFoundException extends BusinessException {

  public WalletNotFoundException() {
    super(ErrorCode.WALLET_NOT_FOUND);
  }

  public WalletNotFoundException(String walletAddress) {
    super(ErrorCode.WALLET_NOT_FOUND, "Requested wallet is not found. wallet_address = " + walletAddress);
  }
}
