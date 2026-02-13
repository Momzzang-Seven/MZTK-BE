package momzzangseven.mztkbe.global.error.wallet;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class WalletBlackListException extends BusinessException {

  public WalletBlackListException(String walletAddress) {
    super(ErrorCode.WALLET_IN_BLACKLIST, walletAddress);
  }
}
