package momzzangseven.mztkbe.global.error.web3;

import momzzangseven.mztkbe.global.error.BusinessException;

public class Web3TransactionStateInvalidException extends BusinessException {

  public Web3TransactionStateInvalidException(String message) {
    super(Web3ErrorCode.WEB3_TRANSACTION_STATE_INVALID, message);
  }

  public Web3TransactionStateInvalidException(String message, Throwable cause) {
    super(Web3ErrorCode.WEB3_TRANSACTION_STATE_INVALID, message, cause);
  }
}
