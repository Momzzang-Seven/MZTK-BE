package momzzangseven.mztkbe.global.error.web3;

import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;

public class Web3TransactionStateInvalidException extends BusinessException {

  public Web3TransactionStateInvalidException(String message) {
    super(ErrorCode.WEB3_TRANSACTION_STATE_INVALID, message);
  }

  public Web3TransactionStateInvalidException(String message, Throwable cause) {
    super(ErrorCode.WEB3_TRANSACTION_STATE_INVALID, message, cause);
  }
}
