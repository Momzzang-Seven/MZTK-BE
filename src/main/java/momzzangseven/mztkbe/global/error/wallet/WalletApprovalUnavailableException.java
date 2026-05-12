package momzzangseven.mztkbe.global.error.wallet;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class WalletApprovalUnavailableException extends BusinessException {

  public WalletApprovalUnavailableException() {
    super(ErrorCode.WALLET_APPROVAL_UNAVAILABLE);
  }

  public WalletApprovalUnavailableException(String reason) {
    super(ErrorCode.WALLET_APPROVAL_UNAVAILABLE, reason);
  }
}
