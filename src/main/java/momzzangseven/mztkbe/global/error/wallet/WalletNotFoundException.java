package momzzangseven.mztkbe.global.error.wallet;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class WalletNotFoundException extends BusinessException {

  public WalletNotFoundException() {
    super(ErrorCode.WALLET_NOT_FOUND);
  }

  public WalletNotFoundException(Long walletId) {
    super(ErrorCode.WALLET_NOT_FOUND, "Requested wallet id is not found. walled_id = " + walletId);
  }
}
