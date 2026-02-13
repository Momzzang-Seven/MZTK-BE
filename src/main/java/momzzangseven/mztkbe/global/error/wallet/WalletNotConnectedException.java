package momzzangseven.mztkbe.global.error.wallet;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class WalletNotConnectedException extends BusinessException {

  public WalletNotConnectedException() {
    super(ErrorCode.WALLET_NOT_CONNECTED);
  }

  public WalletNotConnectedException(Long userId) {
    super(ErrorCode.WALLET_NOT_CONNECTED, "No ACTIVE wallet connected. userId=" + userId);
  }
}
