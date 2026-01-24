package momzzangseven.mztkbe.global.error.wallet;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class WalletAlreadyExistsException extends BusinessException {

  public WalletAlreadyExistsException() {
    super(ErrorCode.WALLET_ALREADY_EXISTS);
  }

  public WalletAlreadyExistsException(String customMessage) {
    super(ErrorCode.WALLET_ALREADY_EXISTS, customMessage);
  }
}
