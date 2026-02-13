package momzzangseven.mztkbe.global.error.challenge;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class ChallengeMismatchWalletAddressException extends BusinessException {
  public ChallengeMismatchWalletAddressException() {
    super(ErrorCode.CHALLENGE_WALLET_MISMATCH);
  }
}
