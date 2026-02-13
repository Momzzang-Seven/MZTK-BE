package momzzangseven.mztkbe.global.error.wallet;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class WalletAlreadyLinkedException extends BusinessException {

  public WalletAlreadyLinkedException() {
    super(ErrorCode.WALLET_ALREADY_LINKED);
  }

  public WalletAlreadyLinkedException(String userId) {
    super(
        ErrorCode.WALLET_ALREADY_LINKED,
        ErrorCode.WALLET_ALREADY_LINKED.getMessage() + ". userId: " + userId);
  }
}
