package momzzangseven.mztkbe.global.error.wallet;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class UnauthorizedWalletAccessException extends BusinessException {
  public UnauthorizedWalletAccessException() {
    super(ErrorCode.WALLET_UNAUTHORIZED_ACCESS);
  }

  public UnauthorizedWalletAccessException(Long walletId, Long userId) {
    super(
        ErrorCode.WALLET_UNAUTHORIZED_ACCESS,
        "Given wallet id(" + walletId + ") is not belongs to given user id(" + userId + ").");
  }
}
